// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.PairProcessor;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.AppendablePersistentMap;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.*;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

@ApiStatus.Internal
public class ObjectObjectPersistentMultiMaplet<K, V> extends ObjectObjectMultiMaplet<K, V>{
  private static final Collection<?> NULL_COLLECTION = Collections.emptySet();
  private static final int CACHE_SIZE = 512;
  private final PersistentHashMap<K, Collection<V>> myMap;
  private final DataExternalizer<V> myValueExternalizer;
  private final SLRUCache<K, Collection<V>> myCache;

  public ObjectObjectPersistentMultiMaplet(final File file,
                                        final KeyDescriptor<K> keyExternalizer,
                                        final DataExternalizer<V> valueExternalizer,
                                        final Supplier<? extends Collection<V>> collectionFactory) throws IOException {
    myValueExternalizer = valueExternalizer;
    myMap = new PersistentHashMap<>(file, keyExternalizer, new CollectionDataExternalizer<>(valueExternalizer, collectionFactory));
    myCache = new SLRUCache<>(CACHE_SIZE, CACHE_SIZE * 3, keyExternalizer) {
      @Override
      public @NotNull Collection<V> createValue(K key) {
        try {
          final Collection<V> collection = myMap.get(key);
          //noinspection unchecked
          return collection == null? (Collection<V>)NULL_COLLECTION : collection;
        }
        catch (IOException e) {
          throw new BuildDataCorruptedException(e);
        }
      }
    };
  }


  @Override
  public boolean containsKey(final K key) {
    try {
      return myMap.containsMapping(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public Collection<V> get(final K key) {
    final Collection<V> collection = myCache.get(key);
    return collection == NULL_COLLECTION? null : collection;
  }

  @Override
  public void replace(K key, Collection<V> value) {
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
  public void put(final K key, final Collection<V> value) {
    try {
      myCache.remove(key);
      myMap.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
        @Override
        public void append(@NotNull DataOutput out) throws IOException {
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
  public void put(final K key, final V value) {
    put(key, Collections.singleton(value));
  }

  @Override
  public void removeAll(K key, Collection<V> values) {
    try {
      final Collection<V> collection = myCache.get(key);

      if (collection != NULL_COLLECTION) {
        if (collection.removeAll(values)) {
          myCache.remove(key);
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
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void removeFrom(final K key, final V value) {
    try {
      final Collection<V> collection = myCache.get(key);

      if (collection != NULL_COLLECTION) {
        if (collection.remove(value)) {
          myCache.remove(key);
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
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void remove(final K key) {
    try {
      myCache.remove(key);
      myMap.remove(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void putAll(ObjectObjectMultiMaplet<K, V> m) {
    m.forEachEntry((key, value) -> {
      put(key, value);
      return true;
    });
  }

  @Override
  public void replaceAll(ObjectObjectMultiMaplet<K, V> m) {
    m.forEachEntry((key, value) -> {
      replace(key, value);
      return true;
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

  @Override
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
  void forEachEntry(@NotNull PairProcessor<? super K, ? super Collection<V>> procedure) {
    try {
      myMap.processKeysWithExistingMapping(key -> {
        try {
          return procedure.process(key, myMap.get(key));
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

  private static final class CollectionDataExternalizer<V> implements DataExternalizer<Collection<V>> {
    private final DataExternalizer<V> myElementExternalizer;
    private final Supplier<? extends Collection<V>> myCollectionFactory;

    CollectionDataExternalizer(DataExternalizer<V> elementExternalizer, Supplier<? extends Collection<V>> collectionFactory) {
      myElementExternalizer = elementExternalizer;
      myCollectionFactory = collectionFactory;
    }

    @Override
    public void save(final @NotNull DataOutput out, final Collection<V> value) throws IOException {
      for (V x : value) {
        myElementExternalizer.save(out, x);
      }
    }

    @Override
    public Collection<V> read(final @NotNull DataInput in) throws IOException {
      final Collection<V> result = myCollectionFactory.get();
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        result.add(myElementExternalizer.read(in));
      }
      return result;
    }
  }
}
