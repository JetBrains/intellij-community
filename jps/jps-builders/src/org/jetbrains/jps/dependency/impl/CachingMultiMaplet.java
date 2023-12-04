// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.javac.Iterators;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class CachingMultiMaplet<K, V> implements MultiMaplet<K, V> {

  private final LoadingCache<K, Iterable<V>> myCache;
  private final MultiMaplet<K, V> myDelegate;

  public CachingMultiMaplet(MultiMaplet<K, V> delegate, int maxCacheSize) {
    myDelegate = delegate;
    myCache = Caffeine.newBuilder().maximumSize(maxCacheSize).build(myDelegate::get);
  }

  @Override
  public boolean containsKey(K key) {
    return myCache.getIfPresent(key) != null || myDelegate.containsKey(key);
  }

  @Override
  public @NotNull Iterable<V> get(K key) {
    return myCache.get(key);
  }

  @Override
  public void put(K key, @NotNull Iterable<? extends V> values) {
    try {
      myDelegate.put(key, values);
    }
    finally {
      myCache.invalidate(key);
    }
  }

  @Override
  public void remove(K key) {
    try {
      myDelegate.remove(key);
    }
    finally {
      myCache.invalidate(key);
    }
  }

  @Override
  public void appendValue(K key, V value) {
    appendValues(key, Collections.singleton(value));
  }

  @Override
  public void appendValues(K key, @NotNull Iterable<? extends V> values) {
    if (!Iterators.isEmpty(values)) {
      try {
        myDelegate.appendValues(key, values);
      }
      finally {
        myCache.invalidate(key);
      }
    }
  }

  @Override
  public void removeValue(K key, V value) {
    removeValues(key, Collections.singleton(value));
  }

  @Override
  public void removeValues(K key, @NotNull Iterable<? extends V> values) {
    if (!Iterators.isEmpty(values)) {
      Set<V> collection = Iterators.collect(myCache.get(key), new LinkedHashSet<>());
      if (collection.removeAll(values instanceof Set? ((Set<? extends V>)values) : Iterators.collect(values, new SmartHashSet<>()))) {
        if (collection.isEmpty()) {
          remove(key);
        }
        else {
          myDelegate.put(key, collection);
          myCache.put(key, collection);
        }
      }
    }
  }

  @Override
  public @NotNull Iterable<K> getKeys() {
    return myDelegate.getKeys();
  }

  @Override
  public void close() throws IOException {
    myCache.invalidateAll();
    myDelegate.close();
  }

  @Override
  public void flush() throws IOException {
    myDelegate.flush();
  }
}
