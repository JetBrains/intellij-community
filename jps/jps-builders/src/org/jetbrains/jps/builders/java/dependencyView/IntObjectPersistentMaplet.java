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

import com.intellij.util.Processor;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IntInlineKeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import gnu.trove.TIntObjectProcedure;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/10/12
 */
public class IntObjectPersistentMaplet<V> extends IntObjectMaplet<V>{

  private static final Object NULL_OBJ = new Object();
  private static final int CACHE_SIZE = 512;
  private final PersistentHashMap<Integer, V> myMap;
  private final SLRUCache<Integer, Object> myCache;

  public IntObjectPersistentMaplet(final File file, final DataExternalizer<V> externalizer) {
    try {
      myMap = new PersistentHashMap<Integer, V>(file, new IntInlineKeyDescriptor(), externalizer);
      myCache = new SLRUCache<Integer, Object>(CACHE_SIZE, CACHE_SIZE) {
        @NotNull
        @Override
        public Object createValue(Integer key) {
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
  public boolean containsKey(final int key) {
    try {
      return myMap.containsMapping(key);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public V get(final int key) {
    final Object obj = myCache.get(key);
    return obj == NULL_OBJ? null : (V)obj;
  }

  @Override
  public void put(final int key, final V value) {
    try {
      myCache.remove(key);
      myMap.put(key, value);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void putAll(final IntObjectMaplet<V> m) {
    m.forEachEntry(new TIntObjectProcedure<V>() {
      @Override
      public boolean execute(int key, V value) {
        put(key, value);
        return true;
      }
    });
  }

  @Override
  public void remove(final int key) {
    try {
      myCache.remove(key);
      myMap.remove(key);
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
  public void forEachEntry(final TIntObjectProcedure<V> proc) {
    try {
      myMap.processKeysWithExistingMapping(new Processor<Integer>() {
        @Override
        public boolean process(Integer key) {
          try {
            final V value = myMap.get(key);
            return value == null? proc.execute(key, null) : proc.execute(key, value);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
