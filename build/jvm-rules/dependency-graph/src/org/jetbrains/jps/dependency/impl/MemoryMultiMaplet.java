// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.util.Iterators;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
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
    return col != null? col : myEmptyCollection;
  }

  @Override
  public void put(K key, @NotNull Iterable<? extends V> values) {
    //noinspection unchecked
    if (Iterators.isEmpty(values)) {
      myMap.remove(key);
    }
    else {
      myMap.put(key, Iterators.collect(values, myCollectionFactory.get()));
    }
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
  public void appendValues(K key, @NotNull Iterable<? extends V> toAppend) {
    if (!Iterators.isEmpty(toAppend)) {
      C values = myMap.get(key);
      if (values == null) {
        myMap.put(key, values = myCollectionFactory.get());
      }
      for (V val : toAppend) {
        values.add(val);
      }
    }
  }

  @Override
  public void removeValue(K key, V value) {
    C values = myMap.get(key);
    if (values != null) {
      values.remove(value);
    }
  }

  @Override
  public void removeValues(K key, @NotNull Iterable<? extends V> toRemove) {
    if (!Iterators.isEmpty(toRemove)) {
      C values = myMap.get(key);
      if (values != null) {
        for (V value : toRemove) {
          values.remove(value);
        }
      }
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

  @Override
  public void update(K key, @NotNull Iterable<V> dataAfter, BiFunction<? super Iterable<V>, ? super Iterable<V>, Difference.Specifier<? extends V, ?>> diffComparator) {
    put(key, dataAfter);
  }
}
