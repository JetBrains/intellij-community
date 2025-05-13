// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.type.DataType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.Maplet;

import java.io.IOException;

public final class PersistentMVStoreMaplet<K, V> implements Maplet<K, V> {
  private final MVMap<K, V> myMap;

  public PersistentMVStoreMaplet(MVStore store, String mapName, DataType<K> keyType, DataType<V> valueType) {
    myMap = store.openMap(mapName, new MVMap.Builder<K, V>().keyType(keyType).valueType(valueType));
  }

  @Override
  public boolean containsKey(K key) {
    return myMap.containsKey(key);
  }

  @Override
  public @Nullable V get(K key) {
    return myMap.get(key);
  }

  @Override
  public void put(K key, V value) {
    if (value == null) {
      myMap.remove(key);
    }
    else {
      myMap.put(key, value);
    }
  }

  @Override
  public void remove(K key) {
    myMap.remove(key);
  }

  @Override
  public Iterable<K> getKeys() {
    return myMap.keySet();
  }

  @Override
  public void close() throws IOException{
  }

  @Override
  public void flush() throws IOException {
  }
}
