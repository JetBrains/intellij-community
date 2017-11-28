/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui.util;

import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public abstract class SynchronizedBidiMultiMap<K, V> {
  private final Map<K, V[]> myKey2Values = new THashMap<>();
  private final Map<V, K> myValue2Keys = new THashMap<>();

  public synchronized Collection<K> keys() {
    return new ArrayList<>(myKey2Values.keySet());
  }

  public synchronized boolean containsKey(K key) {
    return myKey2Values.containsKey(key);
  }

  public synchronized boolean containsValue(V value) {
    return myValue2Keys.containsKey(value);
  }

  public synchronized K getKeyFor(V value) {
    return myValue2Keys.get(value);
  }

  public synchronized V[] get(K key) {
    return myKey2Values.get(key);
  }
  public synchronized V[] getOrDefault(K key, V[] defaultValue) {
    V[] values = get(key);
    return values == null ? defaultValue : values;
  }

  public synchronized void put(K key, V... values) {
    myKey2Values.merge(key, values, this::merge);
    for (V value : values) {
      myValue2Keys.put(value, key);
    }
  }

  /**
   * @return new elements or null!
   */
  public synchronized V[] remove(K key, V value) {
    V[] newValues = myKey2Values.computeIfPresent(key, (k, vs) -> {
      V[] removed = ArrayUtil.remove(vs, value, arrayFactory());
      if (removed.length == 0) return null;
      return removed;
    });
    myValue2Keys.remove(value);
    return newValues;
  }

  public synchronized K removeValue(V value) {
    K key = myValue2Keys.get(value);
    if (key != null) {
      remove(key, value);
    }
    return key;
  }

  public synchronized V[] remove(K key) {
    V[] removed = myKey2Values.remove(key);
    if (removed != null) {
      for (V v : removed) {
        myValue2Keys.remove(v);
      }
    }
    return removed;
  }

  public synchronized Collection<V> getValues() {
    return myValue2Keys.keySet();
  }

  public synchronized boolean isEmpty() {
    return myValue2Keys.isEmpty();
  }

  public abstract V[] merge(V[] values1, V[] values2);

  public abstract ArrayFactory<V> arrayFactory();
}
