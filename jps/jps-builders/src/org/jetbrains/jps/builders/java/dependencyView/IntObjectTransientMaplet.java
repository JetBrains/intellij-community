/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java.dependencyView;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/10/12
 */
public class IntObjectTransientMaplet<V> extends IntObjectMaplet<V>{
  private final TIntObjectHashMap<V> myMap = new TIntObjectHashMap<V>();
  @Override
  boolean containsKey(int key) {
    return myMap.containsKey(key);
  }

  @Override
  V get(int key) {
    return myMap.get(key);
  }

  @Override
  void put(int key, V value) {
    myMap.put(key, value);
  }

  @Override
  void putAll(IntObjectMaplet<V> m) {
    m.forEachEntry(new TIntObjectProcedure<V>() {
      @Override
      public boolean execute(int key, V value) {
        myMap.put(key, value);
        return true;
      }
    });
  }

  @Override
  void remove(int key) {
    myMap.remove(key);
  }

  @Override
  void close() {
    myMap.clear();
  }

  @Override
  void forEachEntry(TIntObjectProcedure<V> proc) {
    myMap.forEachEntry(proc);
  }

  @Override
  void flush(boolean memoryCachesOnly) {
  }
}
