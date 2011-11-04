/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.ether.dependencyView;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 05.11.11
 * Time: 0:05
 * To change this template use File | Settings | File Templates.
 */
public class PersistentMaplet<K, V> implements Maplet<K, V> {
  private final PersistentHashMap<K, V> map;

  public PersistentMaplet(final File file, final KeyDescriptor<K> k, final DataExternalizer<V> v) {
    try {
      map = new PersistentHashMap<K, V>(file, k, v);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean containsKey(final Object key) {
    try {
      return map.containsMapping((K)key);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public V get(final Object key) {
    try {
      return map.get((K)key);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void put(final K key, final V value) {
    try {
      map.put(key, value);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void putAll(final Maplet<K, V> m) {
    try {
      for (Map.Entry<K, V> e : m.entrySet()) {
        map.put(e.getKey(), e.getValue());
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void remove(final Object key) {
    try {
      map.remove((K)key);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    try {
      map.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Collection<K> keyCollection() {
    try {
      return map.getAllKeysWithExistingMapping();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    assert (false);
    return null;
  }
}
