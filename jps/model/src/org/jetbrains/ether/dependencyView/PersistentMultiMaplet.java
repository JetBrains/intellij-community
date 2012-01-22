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

import java.io.*;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 08.03.11
 * Time: 15:38
 * To change this template use File | Settings | File Templates.
 */
class PersistentMultiMaplet<K, V> implements MultiMaplet<K, V> {
  private final PersistentHashMap<K, Collection<V>> myMap;
  private final DataExternalizer<V> myValueExternalizer;

  public PersistentMultiMaplet(final File file,
                               final KeyDescriptor<K> keyExternalizer,
                               final DataExternalizer<V> valueExternalizer,
                               final TransientMultiMaplet.CollectionConstructor<V> collectionFactory) throws IOException {
    myValueExternalizer = valueExternalizer;
    myMap = new PersistentHashMap<K, Collection<V>>(file, keyExternalizer,
                                                    new CollectionDataExternalizer<V>(valueExternalizer, collectionFactory));
  }


  @Override
  public boolean containsKey(final K key) {
    try {
      return myMap.containsMapping(key);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Collection<V> get(final K key) {
    try {
      return myMap.get(key);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void put(final K key, final Collection<V> value) {
    try {
      myMap.appendData(key, new PersistentHashMap.ValueDataAppender() {
        public void append(DataOutput out) throws IOException {
          for (V v : value) {
            myValueExternalizer.save(out, v);
          }
        }
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void put(final K key, final V value) {
    put(key, Collections.singleton(value));
  }

  @Override
  public void removeAll(K key, Collection<V> values) {
    try {
      final Collection<V> collection = myMap.get(key);

      if (collection != null) {
        if (collection.removeAll(values)) {
          if (collection.isEmpty()) {
            myMap.remove(key);
          }
          else {
            myMap.put(key, collection);
          }
        }
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void removeFrom(final K key, final V value) {
    try {
      final Collection<V> collection = myMap.get(key);

      if (collection != null) {
        if (collection.remove(value)) {
          if (collection.isEmpty()) {
            myMap.remove(key);
          }
          else {
            myMap.put(key, collection);
          }
        }
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void remove(final K key) {
    try {
      myMap.remove(key);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void putAll(MultiMaplet<K, V> m) {
    for (Map.Entry<K, Collection<V>> entry : m.entrySet()) {
      final K key = entry.getKey();
      remove(key);
      put(key, entry.getValue());
    }
  }

  public Collection<K> keyCollection() {
    try {
      return myMap.getAllKeysWithExistingMapping();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    try {
      myMap.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void flush() {
    myMap.force();
  }

  @Override
  public Collection<Map.Entry<K, Collection<V>>> entrySet() {
    final Collection<Map.Entry<K, Collection<V>>> result = new LinkedList<Map.Entry<K, Collection<V>>>();

    try {
      for (final K key : myMap.getAllKeysWithExistingMapping()) {
        final Collection<V> value = myMap.get(key);

        final Map.Entry<K, Collection<V>> entry = new Map.Entry<K, Collection<V>>() {
          @Override
          public K getKey() {
            return key;
          }

          @Override
          public Collection<V> getValue() {
            return value;
          }

          @Override
          public Collection<V> setValue(Collection<V> value) {
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

  private static class CollectionDataExternalizer<V> implements DataExternalizer<Collection<V>> {
    private final DataExternalizer<V> myElementExternalizer;
    private final TransientMultiMaplet.CollectionConstructor<V> myCollectionFactory;

    public CollectionDataExternalizer(DataExternalizer<V> elementExternalizer,
                                      TransientMultiMaplet.CollectionConstructor<V> collectionFactory) {
      myElementExternalizer = elementExternalizer;
      myCollectionFactory = collectionFactory;
    }

    @Override
    public void save(final DataOutput out, final Collection<V> value) throws IOException {
      for (V x : value) {
        myElementExternalizer.save(out, x);
      }
    }

    @Override
    public Collection<V> read(final DataInput in) throws IOException {
      final Collection<V> result = myCollectionFactory.create();
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        result.add(myElementExternalizer.read(in));
      }
      return result;
    }
  }
}
