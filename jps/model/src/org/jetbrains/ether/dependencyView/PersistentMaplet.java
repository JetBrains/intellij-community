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

import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 05.11.11
 * Time: 0:05
 * To change this template use File | Settings | File Templates.
 */
public class PersistentMaplet<K, V> implements Maplet<K, V> {
  private static final Object NULL_OBJ = new Object();
  private static final int CACHE_SIZE = 512;
  private final PersistentHashMap<K, V> myMap;
  private final SLRUCache<K, Object> myCache;

  public PersistentMaplet(final File file, final KeyDescriptor<K> k, final DataExternalizer<V> v) {
    try {
      myMap = new PersistentHashMap<K, V>(file, k, v);
      myCache = new SLRUCache<K, Object>(CACHE_SIZE, CACHE_SIZE) {
        @NotNull
        @Override
        public Object createValue(K key) {
          try {
            final V v1 = myMap.get(key);
            return v1 == null? NULL_OBJ : v1;
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean containsKey(final Object key) {
    try {
      return myMap.containsMapping((K)key);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public V get(final Object key) {
    final Object obj = myCache.get((K)key);
    return obj == NULL_OBJ? null : (V)obj;
  }

  @Override
  public void put(final K key, final V value) {
    try {
      myCache.remove(key);
      myMap.put(key, value);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void putAll(final Maplet<K, V> m) {
    for (Map.Entry<K, V> e : m.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  @Override
  public void remove(final Object key) {
    try {
      final K _key = (K)key;
      myCache.remove(_key);
      myMap.remove(_key);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    try {
      myCache.clear();
      myMap.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void flush(boolean memoryCachesOnly) {
    if (memoryCachesOnly) {
      if (myMap.isDirty()) {
        myMap.dropMemoryCaches();
      }
    }
    else {
      myMap.force();
    }
  }

  @Override
  public Collection<K> keyCollection() {
    try {
      return myMap.getAllKeysWithExistingMapping();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Collection<Map.Entry<K, V>> entrySet() {
    final Collection<Map.Entry<K, V>> result = new LinkedList<Map.Entry<K, V>>();

    try {
      for (final K key : myMap.getAllKeysWithExistingMapping()) {
        final V value = get(key);

        final Map.Entry<K, V> entry = new Map.Entry<K, V>() {
          @Override
          public K getKey() {
            return key;
          }

          @Override
          public V getValue() {
            return value;
          }

          @Override
          public V setValue(V value) {
            return null;
          }
        };

        result.add(entry);
      }

      return result;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
