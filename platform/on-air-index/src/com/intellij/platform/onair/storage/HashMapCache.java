// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.storage;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

public class HashMapCache<K, V> implements Cache<K, V> {
  private final HashMap<K, V> data = new HashMap<>();

  @Override
  public V getIfPresent(@NotNull Object key) {
    //noinspection SuspiciousMethodCalls
    return data.get(key);
  }

  @Override
  public V get(@NotNull K key, @NotNull Callable<? extends V> loader) throws ExecutionException {
    V result = data.get(key);
    if (result == null) {
      try {
        result = loader.call();
        data.put(key, result);
      }
      catch (Exception e) {
        throw new ExecutionException(e);
      }
    }
    return result;
  }

  @Override
  public ImmutableMap<K, V> getAllPresent(@NotNull Iterable<?> keys) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void put(@NotNull K key, @NotNull V value) {
    data.put(key, value);
  }

  @Override
  public void putAll(@NotNull Map<? extends K, ? extends V> m) {
    data.putAll(m);
  }

  @Override
  public void invalidate(@NotNull Object key) {
    //noinspection SuspiciousMethodCalls
    data.remove(key);
  }

  @Override
  public void invalidateAll(@NotNull Iterable<?> keys) {
    keys.forEach(this::invalidate);
  }

  @Override
  public void invalidateAll() {
    data.clear();
  }

  @Override
  public long size() {
    return data.size();
  }

  @Override
  public CacheStats stats() {
    return null;
  }

  @Override
  public ConcurrentMap<K, V> asMap() {
    return new ConcurrentHashMap<>(data);
  }

  @Override
  public void cleanUp() {
    invalidateAll();
  }
}
