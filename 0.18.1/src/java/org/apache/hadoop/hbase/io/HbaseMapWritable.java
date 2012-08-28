/**
 * Copyright 2008 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HStoreKey;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.util.ReflectionUtils;

/**
 * A Writable Map.
 * Like {@link org.apache.hadoop.io.MapWritable} but dumb. It will fail
 * if passed a Writable it has not already been told about. Its also been
 * primed with hbase Writables.  Keys are always byte arrays.  Thats other
 * difference from MapWritable.
 *
 * @param <K> key
 * @param <V> value
 */
public class HbaseMapWritable <K, V>
implements SortedMap<byte [], V>, Writable, Configurable {
  private AtomicReference<Configuration> conf =
    new AtomicReference<Configuration>();
  
  // Static maps of code to class and vice versa.  Includes types used in hbase
  // only.
  static final Map<Byte, Class<?>> CODE_TO_CLASS =
    new HashMap<Byte, Class<?>>();
  static final Map<Class<?>, Byte> CLASS_TO_CODE =
    new HashMap<Class<?>, Byte>();

  static {
    byte code = 0;
    addToMap(HStoreKey.class, code++);
    addToMap(ImmutableBytesWritable.class, code++);
    addToMap(Text.class, code++);
    addToMap(Cell.class, code++);
    addToMap(byte [].class, code++);
  }

  @SuppressWarnings("boxing")
  private static void addToMap(final Class<?> clazz,
      final byte code) {
    CLASS_TO_CODE.put(clazz, code);
    CODE_TO_CLASS.put(code, clazz);
  }
  
  private SortedMap<byte [], V> instance =
    new TreeMap<byte [], V>(Bytes.BYTES_COMPARATOR);

  /** @return the conf */
  public Configuration getConf() {
    return conf.get();
  }

  /** @param conf the conf to set */
  public void setConf(Configuration conf) {
    this.conf.set(conf);
  }

  public void clear() {
    instance.clear();
  }

  public boolean containsKey(Object key) {
    return instance.containsKey(key);
  }

  public boolean containsValue(Object value) {
    return instance.containsValue(value);
  }

  public Set<Entry<byte [], V>> entrySet() {
    return instance.entrySet();
  }

  public V get(Object key) {
    return instance.get(key);
  }
  
  public boolean isEmpty() {
    return instance.isEmpty();
  }

  public Set<byte []> keySet() {
    return instance.keySet();
  }

  public int size() {
    return instance.size();
  }

  public Collection<V> values() {
    return instance.values();
  }

  public void putAll(Map<? extends byte [], ? extends V> m) {
    this.instance.putAll(m);
  }

  public V remove(Object key) {
    return this.instance.remove(key);
  }

  public V put(byte [] key, V value) {
    return this.instance.put(key, value);
  }

  public Comparator<? super byte[]> comparator() {
    return this.instance.comparator();
  }

  public byte[] firstKey() {
    return this.instance.firstKey();
  }

  public SortedMap<byte[], V> headMap(byte[] toKey) {
    return this.instance.headMap(toKey);
  }

  public byte[] lastKey() {
    return this.instance.lastKey();
  }

  public SortedMap<byte[], V> subMap(byte[] fromKey, byte[] toKey) {
    return this.instance.subMap(fromKey, toKey);
  }

  public SortedMap<byte[], V> tailMap(byte[] fromKey) {
    return this.instance.tailMap(fromKey);
  }
  
  // Writable

  /** @return the Class class for the specified id */
  @SuppressWarnings({ "unchecked", "boxing" })
  protected Class<?> getClass(byte id) {
    return CODE_TO_CLASS.get(id);
  }

  /** @return the id for the specified Class */
  @SuppressWarnings({ "unchecked", "boxing" })
  protected byte getId(Class<?> clazz) {
    Byte b = CLASS_TO_CODE.get(clazz);
    if (b == null) {
      throw new NullPointerException("Nothing for : " + clazz);
    }
    return b;
  }
  
  @Override
  public String toString() {
    return this.instance.toString();
  }

  public void write(DataOutput out) throws IOException {
    // Write out the number of entries in the map
    out.writeInt(this.instance.size());

    // Then write out each key/value pair
    for (Map.Entry<byte [], V> e: instance.entrySet()) {
      Bytes.writeByteArray(out, e.getKey());
      out.writeByte(getId(e.getValue().getClass()));
      ((Writable)e.getValue()).write(out);
    }
  }

  @SuppressWarnings("unchecked")
  public void readFields(DataInput in) throws IOException {
    // First clear the map.  Otherwise we will just accumulate
    // entries every time this method is called.
    this.instance.clear();
    
    // Read the number of entries in the map
    int entries = in.readInt();
    
    // Then read each key/value pair
    for (int i = 0; i < entries; i++) {
      byte [] key = Bytes.readByteArray(in);
      Writable value = (Writable)ReflectionUtils.
        newInstance(getClass(in.readByte()), getConf());
      value.readFields(in);
      V v = (V)value;
      this.instance.put(key, v);
    }
  }
}