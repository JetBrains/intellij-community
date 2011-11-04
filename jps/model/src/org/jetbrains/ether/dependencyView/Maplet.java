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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 03.11.11
 * Time: 21:01
 * To change this template use File | Settings | File Templates.
 */
interface Maplet<K, V> {
  boolean containsKey(final Object key);
  Collection<V> get(final Object key);
  Collection<V> put(final K key, final V value);
  Collection<V> put(final K key, final Collection<V> value);
  void putAll(Maplet<K, V> m);
  Collection<V> remove(final Object key);
  void removeFrom(final K key, final V value);
  void close();
  Collection<K> keyCollection();
  Set<Map.Entry<K, Collection<V>>> entrySet();
}
