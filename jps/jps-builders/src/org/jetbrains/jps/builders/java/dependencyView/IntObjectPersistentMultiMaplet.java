/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import gnu.trove.TIntObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.*;
import java.util.Collection;
import java.util.Collections;

/**
 * @author: db
 */
public class IntObjectPersistentMultiMaplet<V> extends IntObjectMultiMaplet<V> {
  private static final Collection NULL_COLLECTION = Collections.emptySet();
  private static final int CACHE_SIZE = 128;
  private final PersistentHashMap<Integer, Collection<V>> myMap;
  private final DataExternalizer<V> myValueExternalizer;
  private final SLRUCache<Integer, Collection> myCache;

  public IntObjectPersistentMultiMaplet(final File file,
                                        final KeyDescriptor<Integer> keyExternalizer,
                                        final DataExternalizer<V> valueExternalizer,
                                        final CollectionFactory<V> collectionFactory) throws IOException {
    myValueExternalizer = valueExternalizer;
    myMap = new PersistentHashMap<>(file, keyExternalizer,
                                    new CollectionDataExternalizer<>(valueExternalizer, collectionFactory));
    myCache = new SLRUCache<Integer, Collection>(CACHE_SIZE, CACHE_SIZE) {
      @NotNull
      @Override
      public Collection createValue(Integer key) {
        try {
          final Collection<V> collection = myMap.get(key);
          return collection == null? NULL_COLLECTION : collection;
        }
        catch (IOException e) {
          throw new BuildDataCorruptedException(e);
        }
      }
    };
  }


  @Override
  public boolean containsKey(final int key) {
    try {
      return myMap.containsMapping(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public Collection<V> get(final int key) {
    final Collection<V> collection = myCache.get(key);
    return collection == NULL_COLLECTION? null : collection;
  }

  @Override
  public void replace(int key, Collection<V> value) {
    try {
      myCache.remove(key);
      if (value == null || value.isEmpty()) {
        myMap.remove(key);
      }
      else {
        myMap.put(key, value);
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void put(final int key, final Collection<V> value) {
    try {
      myCache.remove(key);
      myMap.appendData(key, new PersistentHashMap.ValueDataAppender() {
        public void append(DataOutput out) throws IOException {
          for (V v : value) {
            myValueExternalizer.save(out, v);
          }
        }
      });
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void put(final int key, final V value) {
    put(key, Collections.singleton(value));
  }

  @Override
  public void removeAll(int key, Collection<V> values) {
    try {
      final Collection collection = myCache.get(key);

      if (collection != NULL_COLLECTION) {
        if (collection.removeAll(values)) {
          myCache.remove(key);
          if (collection.isEmpty()) {
            myMap.remove(key);
          }
          else {
            myMap.put(key, (Collection<V>)collection);
          }
        }
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void removeFrom(final int key, final V value) {
    try {
      final Collection collection = myCache.get(key);

      if (collection != NULL_COLLECTION) {
        if (collection.remove(value)) {
          myCache.remove(key);
          if (collection.isEmpty()) {
            myMap.remove(key);
          }
          else {
            myMap.put(key, (Collection<V>)collection);
          }
        }
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void remove(final int key) {
    try {
      myCache.remove(key);
      myMap.remove(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void putAll(IntObjectMultiMaplet<V> m) {
    m.forEachEntry(new TIntObjectProcedure<Collection<V>>() {
      @Override
      public boolean execute(int key, Collection<V> value) {
        put(key, value);
        return true;
      }
    });
  }

  @Override
  public void replaceAll(IntObjectMultiMaplet<V> m) {
    m.forEachEntry(new TIntObjectProcedure<Collection<V>>() {
      @Override
      public boolean execute(int key, Collection<V> value) {
        replace(key, value);
        return true;
      }
    });
  }

  @Override
  public void close() {
    try {
      myCache.clear();
      myMap.close();
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
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
  public void forEachEntry(final TIntObjectProcedure<Collection<V>> procedure) {
    try {
      myMap.processKeysWithExistingMapping(key -> {
        try {
          return procedure.execute(key, myMap.get(key));
        }
        catch (IOException e) {
          throw new BuildDataCorruptedException(e);
        }
      });
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  private static class CollectionDataExternalizer<V> implements DataExternalizer<Collection<V>> {
    private final DataExternalizer<V> myElementExternalizer;
    private final CollectionFactory<V> myCollectionFactory;

    public CollectionDataExternalizer(DataExternalizer<V> elementExternalizer,
                                      CollectionFactory<V> collectionFactory) {
      myElementExternalizer = elementExternalizer;
      myCollectionFactory = collectionFactory;
    }

    @Override
    public void save(@NotNull final DataOutput out, final Collection<V> value) throws IOException {
      for (V x : value) {
        myElementExternalizer.save(out, x);
      }
    }

    @Override
    public Collection<V> read(@NotNull final DataInput in) throws IOException {
      final Collection<V> result = myCollectionFactory.create();
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        result.add(myElementExternalizer.read(in));
      }
      return result;
    }
  }
}
