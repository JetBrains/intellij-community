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
import org.jetbrains.ether.RW;

import java.io.*;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 08.03.11
 * Time: 15:38
 * To change this template use File | Settings | File Templates.
 */
class PersistentMaplet<K, V> implements Maplet<K, V> {
  private final PersistentHashMap<K, Collection<V>> map;
  private final TransientMaplet.CollectionConstructor<V> constr;

  public PersistentMaplet(final File file,
                          final KeyDescriptor<K> k,
                          final DataExternalizer<V> v,
                          final TransientMaplet.CollectionConstructor<V> c) {
    try {
      map = new PersistentHashMap<K, Collection<V>>(file, k, new DataExternalizer<Collection<V>>() {
        @Override
        public void save(final DataOutput out, final Collection<V> value) throws IOException {
          final int size = value.size();

          out.writeInt(size);

          for (V x : value) {
            v.save(out, x);
          }
        }

        @Override
        public Collection<V> read(final DataInput in) throws IOException {
          final Collection<V> result = c.create();
          final int size = in.readInt();

          for (int i = 0; i < size; i++) {
            result.add(v.read(in));
          }

          return result;
        }
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    constr = c;
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
  public Collection<V> get(final Object key) {
    try {
      return map.get((K)key);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Collection<V> put(final K key, final Collection<V> value) {
    try {
      final Collection<V> x = map.get(key);

      if (x == null) {
        map.put(key, value);
      }
      else {
        x.addAll(value);
      }

      return x;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Collection<V> put(final K key, final V value) {
    final Collection<V> x = constr.create();
    x.add(value);
    return put(key, x);
  }

  @Override
  public void removeFrom(final K key, final V value) {
    try {
      final Object got = map.get(key);

      if (got != null) {
        if (got instanceof Collection) {
          ((Collection)got).remove(value);
        }
        else if (got.equals(value)) {
          map.remove(key);
        }
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Collection<V> remove(final Object key) {
    try {
      map.remove((K)key);
      return null;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void putAll(Maplet<K, V> m) {
    for (Map.Entry<K, Collection<V>> e : m.entrySet()) {
      remove(e.getKey());
      put(e.getKey(), e.getValue());
    }
  }


  public Collection<K> keyCollection() {
    try {
      return map.getAllKeysWithExistingMapping();
    }
    catch (IOException e){
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
  public Set<Map.Entry<K, Collection<V>>> entrySet() {
    assert(false);
    return null;
  }
}
