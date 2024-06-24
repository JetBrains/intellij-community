// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.util.io.AppendablePersistentMap;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.*;
import java.util.Collection;
import java.util.Collections;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

final class IntObjectPersistentMultiMaplet<V> extends IntObjectMultiMaplet<V> {
  private static final Collection<?> NULL_COLLECTION = Collections.emptySet();
  private static final int CACHE_SIZE = 256;
  private final PersistentHashMap<Integer, Collection<V>> map;
  private final DataExternalizer<V> valueExternalizer;
  private final LoadingCache<Integer, Collection<V>> cache;

  IntObjectPersistentMultiMaplet(final File file,
                                        final KeyDescriptor<Integer> keyExternalizer,
                                        final DataExternalizer<V> valueExternalizer,
                                        final Supplier<? extends Collection<V>> collectionFactory) throws IOException {
    this.valueExternalizer = valueExternalizer;
    map = new PersistentHashMap<>(file.toPath(), keyExternalizer, new CollectionDataExternalizer<>(valueExternalizer, collectionFactory));
    cache = Caffeine.newBuilder().maximumSize(CACHE_SIZE).build(key -> {
      try {
        final Collection<V> collection = map.get(key);
        //noinspection unchecked
        return collection == null ? (Collection<V>)NULL_COLLECTION : collection;
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    });
  }

  @Override
  public boolean containsKey(final int key) {
    try {
      return map.containsMapping(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public Collection<V> get(final int key) {
    final Collection<V> collection = cache.get(key);
    return collection == NULL_COLLECTION ? null : collection;
  }

  @Override
  public void replace(int key, Collection<V> value) {
    try {
      cache.invalidate(key);
      if (value == null || value.isEmpty()) {
        map.remove(key);
      }
      else {
        map.put(key, value);
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void put(final int key, final Collection<V> value) {
    try {
      cache.invalidate(key);
      map.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
        @Override
        public void append(@NotNull DataOutput out) throws IOException {
          for (V v : value) {
            valueExternalizer.save(out, v);
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
      if (!values.isEmpty()) {
        final Collection<V> collection = cache.get(key);

        if (collection != NULL_COLLECTION) {
          if (collection.removeAll(values)) {
            cache.invalidate(key);
            if (collection.isEmpty()) {
              map.remove(key);
            }
            else {
              map.put(key, collection);
            }
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
      final Collection<V> collection = cache.get(key);

      if (collection != NULL_COLLECTION) {
        if (collection.remove(value)) {
          cache.invalidate(key);
          if (collection.isEmpty()) {
            map.remove(key);
          }
          else {
            map.put(key, collection);
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
      cache.invalidate(key);
      map.remove(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void putAll(IntObjectMultiMaplet<V> m) {
    m.forEachEntry((vs, value) -> put(value, vs));
  }

  @Override
  public void replaceAll(IntObjectMultiMaplet<V> m) {
    m.forEachEntry((vs, value) -> replace(value, vs));
  }

  @Override
  public void close() {
    try {
      cache.invalidateAll();
      map.close();
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void flush(boolean memoryCachesOnly) {
    if (memoryCachesOnly) {
      if (map.isDirty()) {
        map.dropMemoryCaches();
      }
    }
    else {
      map.force();
    }
  }

  @Override
  void forEachEntry(ObjIntConsumer<? super Collection<V>> procedure) {
    try {
      map.processKeysWithExistingMapping(key -> {
        try {
          procedure.accept(map.get(key), key);
        }
        catch (IOException e) {
          throw new BuildDataCorruptedException(e);
        }
        return true;
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
