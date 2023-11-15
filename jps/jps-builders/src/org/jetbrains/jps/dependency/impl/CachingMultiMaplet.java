// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.javac.Iterators;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class CachingMultiMaplet<K, V> implements MultiMaplet<K, V> {

  private final LoadingCache<K, Iterable<V>> myCache;
  private final MultiMaplet<K, V> myDelegate;

  public CachingMultiMaplet(MultiMaplet<K, V> delegate, int maxCacheSize) {
    myDelegate = delegate;
    myCache = Caffeine.newBuilder().maximumSize(maxCacheSize).build(key -> {
      return myDelegate.get(key);
    });
  }

  @Override
  public boolean containsKey(K key) {
    return myDelegate.containsKey(key);
  }

  @Override
  public @NotNull Iterable<V> get(K key) {
    return myCache.get(key);
  }

  @Override
  public void put(K key, @NotNull Iterable<? extends V> values) {
    myCache.invalidate(key);
    myDelegate.put(key, values);
  }

  @Override
  public void remove(K key) {
    myCache.invalidate(key);
    myDelegate.remove(key);
  }

  @Override
  public void appendValue(K key, V value) {
    appendValues(key, Collections.singleton(value));
  }

  @Override
  public void appendValues(K key, @NotNull Iterable<? extends V> values) {
    if (!Iterators.isEmpty(values)) {
      myCache.invalidate(key);     
      myDelegate.appendValues(key, values);
    }
  }

  @Override
  public void removeValue(K key, V value) {
    removeValues(key, Collections.singleton(value));
  }

  @Override
  public void removeValues(K key, @NotNull Iterable<? extends V> values) {
    if (Iterators.isEmpty(values)) {
      return;
    }
    Iterable<V> currentData = myCache.get(key);
    Collection<V> collection = currentData instanceof Collection? (Collection<V>)currentData : Iterators.collect(currentData, new SmartList<>());
    if (!collection.isEmpty()) {
      boolean changes = false;
      for (V value : values) {
        changes |= collection.remove(value);
      }
      if (changes) {
        myCache.invalidate(key);
        if (collection.isEmpty()) {
          myDelegate.remove(key);
        }
        else {
          myDelegate.put(key, collection);
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
    myCache.invalidateAll();
    myDelegate.flush();
  }
}
