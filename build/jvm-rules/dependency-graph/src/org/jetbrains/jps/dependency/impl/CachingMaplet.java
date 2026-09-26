// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.Maplet;

import java.io.IOException;

public final class CachingMaplet<K, V> implements Maplet<K, V> {
  private static final Object NULL_OBJECT = new Object();
  private final LoadingCache<K, V> myCache;
  private final Maplet<K, V> myDelegate;

  public CachingMaplet(Maplet<K, V> delegate, int maxCacheSize) {
    myDelegate = delegate;
    myCache = Caffeine.newBuilder().maximumSize(maxCacheSize).build(key -> {
      V val = myDelegate.get(key);
      //noinspection unchecked
      return val != null? val : (V)NULL_OBJECT;
    });
  }

  @Override
  public boolean containsKey(K key) {
    return myCache.getIfPresent(key) != null || myDelegate.containsKey(key);
  }

  @Override
  public @Nullable V get(K key) {
    V val = myCache.get(key);
    return val == NULL_OBJECT? null : val;
  }

  @Override
  public void put(K key, @NotNull V value) {
    try {
      myDelegate.put(key, value);
    }
    finally {
      if (myCache.getIfPresent(key) != null) {
        myCache.put(key, value);
      }
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
