// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage.graph;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import com.intellij.util.io.PersistentMapBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.dependency.Maplet;

import java.io.IOException;
import java.nio.file.Path;

public final class PersistentMaplet<K, V> implements Maplet<K, V> {
  private final PersistentHashMap<K, V> myMap;

  public PersistentMaplet(Path mapFile, KeyDescriptor<K> keyDescriptor, DataExternalizer<V> valueExternalizer) {
    try {
      myMap = PersistentMapBuilder.newBuilder(mapFile, keyDescriptor, valueExternalizer).build();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
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
  public @Nullable V get(K key) {
    try {
      return myMap.get(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void put(K key, V value) {
    try {
      if (value == null) {
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
  public void remove(K key) {
    try {
      myMap.remove(key);
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

  @Override
  public void close() throws IOException{
    myMap.close();
  }

  @Override
  public void flush() throws IOException {
    myMap.force();
  }
}
