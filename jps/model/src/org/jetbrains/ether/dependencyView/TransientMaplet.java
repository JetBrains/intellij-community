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

import com.intellij.util.containers.hash.HashMap;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 05.11.11
 * Time: 0:00
 * To change this template use File | Settings | File Templates.
 */
public class TransientMaplet<K, V> implements Maplet<K, V>{
  private final Map<K, V> map = new HashMap<K, V>();
  
  @Override
  public boolean containsKey(final Object key) {
    return map.containsKey(key);
  }

  @Override
  public V get(final Object key) {
    return map.get(key);
  }

  @Override
  public void put(final K key, final V value) {
    map.put(key, value);
  }

  @Override
  public void putAll(final Maplet<K, V> m) {
    for (Map.Entry<K, V> e : m.entrySet()) {
      map.put(e.getKey(), e.getValue());
    }
  }

  @Override
  public void remove(final Object key) {
    map.remove(key);
  }

  @Override
  public void close() {
   
  }

  @Override
  public Collection<K> keyCollection() {
    return map.keySet();
  }

  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    return map.entrySet();
  }
}
