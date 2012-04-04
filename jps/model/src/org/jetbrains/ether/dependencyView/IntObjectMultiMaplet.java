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

import gnu.trove.TIntObjectProcedure;

import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 03.11.11
 * Time: 21:01
 * To change this template use File | Settings | File Templates.
 */
interface IntObjectMultiMaplet<V> {
  boolean containsKey(final int key);
  Collection<V> get(final int key);
  void put(final int key, final V value);
  void put(final int key, final Collection<V> value);
  void replace(final int key, final Collection<V> value);
  void putAll(IntObjectMultiMaplet<V> m);
  void replaceAll(IntObjectMultiMaplet<V> m);
  void remove(final int key);
  void removeFrom(final int key, final V value);
  void removeAll(final int key, final Collection<V> value);
  void close();

  void forEachEntry(TIntObjectProcedure<Collection<V>> procedure);
  void flush(boolean memoryCachesOnly);
}
