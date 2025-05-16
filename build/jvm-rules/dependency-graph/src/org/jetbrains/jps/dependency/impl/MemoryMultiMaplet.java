// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.util.Iterators;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

public final class MemoryMultiMaplet<K, V, C extends Collection<V>> implements MultiMaplet<K, V> {
  private final Map<K, C> myMap = new HashMap<>();
  private final Supplier<? extends C> myCollectionFactory;
  private final C myEmptyCollection;

  public MemoryMultiMaplet(Supplier<? extends C> collectionFactory) {
    myCollectionFactory = collectionFactory;
    C col = collectionFactory.get();
    //noinspection unchecked
    myEmptyCollection = col instanceof List? (C)Collections.emptyList() : col instanceof Set? (C)Collections.emptySet() : col;
  }

  @Override
  public boolean containsKey(K key) {
    return myMap.containsKey(key);
  }

  @Override
  public @NotNull Iterable<V> get(K key) {
    C col = myMap.get(key);
    return col != null? col : Collections.emptySet();
  }

  @Override
  public void put(K key, @NotNull Iterable<? extends V> values) {
    //noinspection unchecked
    myMap.put(key, ensureCollection(values));
  }

  private C ensureCollection(Iterable<? extends V> seq) {
    if (myEmptyCollection instanceof Set && seq instanceof Set) {
      return (C)seq;
    }
    if (myEmptyCollection instanceof List && seq instanceof List) {
      return (C)seq;
    }
    return Iterators.collect(seq, myCollectionFactory.get());
  }

  @Override
  public void remove(K key) {
    myMap.remove(key);
  }

  @Override
  public void appendValue(K key, V value) {
    C values = myMap.get(key);
    if (values == null) {
      myMap.put(key, values = myCollectionFactory.get());
    }
    values.add(value);
  }

  @Override
  public void removeValue(K key, V value) {
    C values = myMap.get(key);
    if (values != null) {
      values.remove(value);
    }
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
