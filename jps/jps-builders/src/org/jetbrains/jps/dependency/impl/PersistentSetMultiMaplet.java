// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.AppendablePersistentMap;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.dependency.SerializableGraphElement;
import org.jetbrains.jps.javac.Iterators;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.function.Supplier;

public final class PersistentSetMultiMaplet<K extends SerializableGraphElement, V extends SerializableGraphElement>
  implements MultiMaplet<K, V> {

  private static final Collection<?> NULL_COLLECTION = Collections.emptyList();
  private static final int CACHE_SIZE = 128;

  private final PersistentHashMap<K, Collection<V>> map;
  private final SLRUCache<K, Collection<V>> cache;

  public PersistentSetMultiMaplet(Path mapFile) {
    try {
      Supplier<Collection<V>> fileCollectionFactory = NodeKeyDescriptor::createNodeSet;
      KeyDescriptor<K> keyDescriptor = NodeKeyDescriptorImpl.getInstance();
      DataExternalizer<Collection<V>> valueExternalizer =
        new CollectionDataExternalizer<>(NodeKeyDescriptorImpl.getInstance(), fileCollectionFactory);
      map = new PersistentHashMap<>(mapFile, keyDescriptor, valueExternalizer);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    cache = SLRUCache.slruCache(CACHE_SIZE, CACHE_SIZE, key -> {
      try {
        Collection<V> collection = map.get(key);
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
      return map.containsMapping(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public @Nullable Iterable<V> get(K key) {
    final Collection<V> collection = cache.get(key);
    return collection == NULL_COLLECTION ? null : collection;
  }

  @Override
  public void put(K key, Iterable<? extends V> values) {
    try {
      cache.remove(key);
      map.put(key, Iterators.collect(values, new HashSet<>()));
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void remove(K key) {
    try {
      cache.remove(key);
      map.remove(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void appendValue(K key, V value) {
    try {
      map.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
        @Override
        public void append(final @NotNull DataOutput out) throws IOException {
          NodeKeyDescriptorImpl.getInstance().save(out, value);
        }
      });
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void removeValue(K key, V value) {
    try {
      final Collection<V> collection = cache.get(key);
      if (collection != NULL_COLLECTION) {
        if (collection.remove(value)) {
          cache.remove(key);
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
  public Iterable<K> getKeys() {
    try {
      return map.getAllKeysWithExistingMapping();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void close() {
    try {
      cache.clear();
      map.close();
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  private static final class CollectionDataExternalizer<V extends SerializableGraphElement> implements DataExternalizer<Collection<V>> {
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
