// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.dependency.NodeSerializerRegistry;
import org.jetbrains.jps.dependency.SerializableGraphElement;

import java.io.*;
import java.util.*;
import java.util.function.Supplier;

public class PersistentSetMultiMaplet<K extends SerializableGraphElement, V extends SerializableGraphElement> implements MultiMaplet<K, V> {
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
      @NotNull
      @Override
      public Collection<V> createValue(K key) {
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
    return collection == NULL_COLLECTION? null : collection;
  }

  @Override
  public void put(K key, Iterable<? extends V> values) { // TODO: ask: почему такой сранный тип?
    //TODO
    //try {
    //  myCache.remove(key);
    //  myMap.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
    //    @Override
    //    public void append(@NotNull final DataOutput out) throws IOException {
    //      ObjectIterator<V> iterator = values.iterator();
    //      while (iterator.hasNext()) {
    //        int value1 = iterator.nextInt();
    //        DataInputOutputUtil.writeINT(out, value1);
    //      }
    //    }
    //  });
    //}
    //catch (IOException e) {
    //  throw new BuildDataCorruptedException(e);
    //}


    //try {
    //  myMap.put(key, (Collection<V>)values);
    //}
    //catch (IOException e) {
    //  throw new BuildDataCorruptedException(e);
    //}



    //List<String> serializedNodes = new ArrayList<>();
    //
    //for (V value : values) {
    //  try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
    //       DataOutputStream dataOut = new DataOutputStream(baos)) {
    //    mySerializerRegistry.getSerializer(0).write(value, dataOut);
    //    serializedNodes.add(baos.toString(StandardCharsets.UTF_8));
    //  }
    //  catch (IOException e) {
    //    throw new RuntimeException(e);
    //  }
    //}
    //
    //myMap.put(key.hashCode(), serializedNodes);
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
    //TODO
    //try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
    //     DataOutputStream dataOut = new DataOutputStream(baos)) {
    //  mySerializerRegistry.getSerializer(0).write(value, dataOut);
    //  myMap.put(key.hashCode(), baos.toString());
    //}
    //catch (IOException e) {
    //  throw new RuntimeException(e);
    //}
  }

  @Override
  public void removeValue(K key, V value) {
    //TODO
    //String serializedValueToRemove;
    //try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
    //     DataOutputStream dataOut = new DataOutputStream(baos)) {
    //  mySerializerRegistry.getSerializer(0).write(value, dataOut);
    //  serializedValueToRemove = baos.toString(StandardCharsets.UTF_8);
    //}
    //catch (IOException e) {
    //  // Обработайте исключение или зарегистрируйте его, если это необходимо
    //  throw new RuntimeException(e);
    //}
    //
    //myMap.removeFrom(key.hashCode(), serializedValueToRemove);
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

  private static class CollectionDataExternalizer<V extends SerializableGraphElement> implements DataExternalizer<Collection<V>> {
    private final DataExternalizer<V> myElementExternalizer;
    private final Supplier<? extends Collection<V>> myCollectionFactory;

    CollectionDataExternalizer(DataExternalizer<V> elementExternalizer, Supplier<? extends Collection<V>> collectionFactory) {
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
      final Collection<V> result = myCollectionFactory.get();
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        result.add(myElementExternalizer.read(in));
      }
      return result;
    }
  }
}
