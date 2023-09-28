// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.AppendablePersistentMap;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.dependency.NodeSerializerRegistry;
import org.jetbrains.jps.dependency.SerializableGraphElement;
import org.jetbrains.jps.javac.Iterators;

import java.io.*;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.function.Supplier;

public final class PersistentSetMultiMaplet<K extends SerializableGraphElement, V extends SerializableGraphElement> implements MultiMaplet<K, V> {
  private final NodeSerializerRegistry mySerializerRegistry;

  private static final Collection<?> NULL_COLLECTION = Collections.emptyList();
  private static final int CACHE_SIZE = 128;
  private final PersistentHashMap<K, Collection<V>> myMap;
  private final SLRUCache<K, Collection<V>> myCache;

  public PersistentSetMultiMaplet(@NotNull NodeSerializerRegistry serializerRegistry) {
    mySerializerRegistry = serializerRegistry;

    try {
      File directory = FileUtil.createTempDirectory("persistent", "map");
      File mapFile = new File(directory, "map");
      if (!mapFile.createNewFile()) throw new IOException("Map file was not created");
      final Supplier<Collection<V>> fileCollectionFactory = NodeKeyDescriptor::createNodeSet;

      KeyDescriptor<K> keyDescriptor = NodeKeyDescriptorImpl.getInstance();
      DataExternalizer<Collection<V>> valueExternalizer =
        new CollectionDataExternalizer<>(NodeKeyDescriptorImpl.getInstance(), fileCollectionFactory);
      myMap = new PersistentHashMap<>(mapFile.toPath(), keyDescriptor, valueExternalizer);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    myCache = new SLRUCache<>(CACHE_SIZE, CACHE_SIZE) {
      @Override
      public @NotNull Collection<V> createValue(K key) {
        try {
          final Collection<V> collection = myMap.get(key);
          //noinspection unchecked
          return collection == null ? (Collection<V>)NULL_COLLECTION : collection;
        }
        catch (IOException e) {
          throw new BuildDataCorruptedException(e);
        }
      }
    };
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
      myCache.remove(key);
      myMap.put(key, Iterators.collect(values, new HashSet<>()));
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void remove(K key) {
    try {
      myCache.remove(key);
      myMap.remove(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void appendValue(K key, V value) {
    try {
      myMap.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
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
  public Iterable<K> getKeys() {
    try {
      return myMap.getAllKeysWithExistingMapping();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
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
