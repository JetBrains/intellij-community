// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.util.io.AppendablePersistentMap;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.dependency.ExternalizableGraphElement;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.javac.Iterators;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class PersistentSetMultiMaplet<K extends ExternalizableGraphElement, V extends ExternalizableGraphElement> implements MultiMaplet<K, V> {
  private static final Iterable<?> NULL_COLLECTION = Collections.emptyList();
  private static final int CACHE_SIZE = 512;
  private final PersistentHashMap<K, Collection<V>> myMap;
  private final LoadingCache<K, Collection<V>> myCache;
  private final DataExternalizer<V> myValuesExternalizer;

  public PersistentSetMultiMaplet(Path mapFile, KeyDescriptor<K> keyDescriptor, DataExternalizer<V> valueExternalizer) {
    try {
      myValuesExternalizer = valueExternalizer;
      myMap = new PersistentHashMap<>(mapFile, keyDescriptor, new DataExternalizer<>() {
        @Override
        public void save(@NotNull DataOutput out, Collection<V> value) throws IOException {
          for (V v : value) {
            valueExternalizer.save(out, v);
          }
        }

        @Override
        public Collection<V> read(@NotNull DataInput in) throws IOException {
          Collection<V> acc = new HashSet<>();
          final DataInputStream stream = (DataInputStream)in;
          while (stream.available() > 0) {
            acc.add(valueExternalizer.read(stream));
          }
          return acc;
        }
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    myCache = Caffeine.newBuilder().maximumSize(CACHE_SIZE).build(key -> {
      try {
        Collection<V> collection = myMap.get(key);
        //noinspection unchecked
        return collection == null ? (Collection<V>)NULL_COLLECTION : collection;
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    });
  }

  @Override
  public boolean containsKey(K key) {
    try {
      return myMap.containsMapping(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public @Nullable Iterable<V> get(K key) {
    final Collection<V> collection = myCache.get(key);
    return collection == NULL_COLLECTION ? null : collection;
  }

  @Override
  public void put(K key, Iterable<? extends V> values) {
    try {
      myCache.invalidate(key);
      Set<V> data = Iterators.collect(values, new HashSet<>());
      if (data.isEmpty()) {
        myMap.remove(key);
      }
      else {
        myMap.put(key, data);
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void remove(K key) {
    try {
      myCache.invalidate(key);
      myMap.remove(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void appendValue(K key, V value) {
    appendValues(key, Collections.singleton(value));
  }

  @Override
  public void appendValues(K key, Iterable<? extends V> values) {
    try {
      myMap.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
        @Override
        public void append(final @NotNull DataOutput out) throws IOException {
          for (V v : values) {
            myValuesExternalizer.save(out, v);
          }
        }
      });
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void removeValue(K key, V value) {
    removeValues(key, Collections.singleton(value));
  }

  @Override
  public void removeValues(K key, Iterable<? extends V> values) {
    try {
      final Collection<V> collection = myCache.get(key);
      if (!collection.isEmpty()) {
        boolean changes = false;
        for (V value : values) {
          changes |= collection.remove(value);
        }
        if (changes) {
          myCache.invalidate(key);
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
  public Iterable<K> getKeys() {
    try {
      return myMap.getAllKeysWithExistingMapping();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void close() {
    try {
      myCache.invalidateAll();
      myMap.close();
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

}
