// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.Maplet;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class MemoryMaplet<K, V> implements Maplet<K, V> {
  private final Map<K, V> myMap = new HashMap<>();

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
    myMap.put(key, value);
  }

  @Override
  public void remove(K key) {
    myMap.remove(key);
  }

  @Override
  public @NotNull Iterable<K> getKeys() {
    return myMap.keySet();
  }

  @Override
  public void close() {
    myMap.clear();
  }

  @Override
  public void flush() throws IOException {
    // empty
  }
}
