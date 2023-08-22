// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.javac.Iterators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MemorySetMultiMaplet<K, V> implements MultiMaplet<K, V> {
  private final Map<K, Set<V>> myMap = new HashMap<>();

  @Override
  public boolean containsKey(K key) {
    return myMap.containsKey(key);
  }

  @Override
  public @Nullable Iterable<V> get(K key) {
    return myMap.get(key);
  }

  @Override
  public void put(K key, Iterable<? extends V> values) {
    myMap.put(key, Iterators.collect(values, new HashSet<>()));
  }

  @Override
  public void remove(K key) {
    myMap.remove(key);
  }

  @Override
  public void appendValue(K key, V value) {
    Set<V> values = myMap.get(key);
    if (values == null) {
      myMap.put(key, values = new HashSet<>());
    }
    values.add(value);
  }

  @Override
  public void removeValue(K key, V value) {
    Set<V> values = myMap.get(key);
    if (values != null) {
      values.remove(value);
    }
  }

  @Override
  public Iterable<K> getKeys() {
    return myMap.keySet();
  }
}
