// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unchecked")
public class ConcurrentObjectCache<K, V> extends ObjectCacheBase<K, V> {

  private static final int DEFAULT_NUMBER_OF_GENERATIONS = 3;

  private final int numberOfGenerations;
  private final int generationSize;
  private final int mask;
  private final CacheEntry<K, V>[] cache;

  public ConcurrentObjectCache(final int size) {
    this(size, DEFAULT_NUMBER_OF_GENERATIONS);
  }

  public ConcurrentObjectCache(final int size, final int numberOfGenerations) {
    super(size);
    this.numberOfGenerations = numberOfGenerations;
    generationSize = CacheUtil.getFloorPrime(size / numberOfGenerations);
    mask = (1 << CacheUtil.integerLogarithm(generationSize)) - 1;
    cache = new CacheEntry[numberOfGenerations * generationSize];
  }

  @Override
  public V tryKeyLocked(@NotNull final K key) {
    return tryKey(key);
  }

  @Override
  public void clear() {
    // do nothing
  }

  @Override
  public void lock() {
  }

  @Override
  public void unlock() {
  }

  @Override
  public V cacheObject(@NotNull final K key, @NotNull final V x) {
    int cacheIndex = CacheUtil.indexFor(key.hashCode(), generationSize, mask) * numberOfGenerations;
    for (int i = 0; i < numberOfGenerations; ++i, ++cacheIndex) {
      final CacheEntry<K, V> entry = cache[cacheIndex];
      if (entry != null && entry.key.equals(key)) {
        cache[cacheIndex] = new CacheEntry<>(key, x);
        // in concurrent environment we can't definitely know if a value is pushed out from the cache
        return null;
      }
    }
    cache[cacheIndex - 1] = new CacheEntry<>(key, x);
    return null;
  }

  @Override
  public V remove(@NotNull final K key) {
    int cacheIndex = CacheUtil.indexFor(key.hashCode(), generationSize, mask) * numberOfGenerations;
    for (int i = 0; i < numberOfGenerations; ++i, ++cacheIndex) {
      final CacheEntry<K, V> entry = cache[cacheIndex];
      if (entry != null && entry.key.equals(key)) {
        final V result = entry.value;
        entry.value = null;
        return result;
      }
    }
    return null;
  }

  @Override
  public V tryKey(@NotNull final K key) {
    int cacheIndex = CacheUtil.indexFor(key.hashCode(), generationSize, mask) * numberOfGenerations;
    CacheEntry<K, V> entry = cache[cacheIndex];
    if (entry != null && entry.key.equals(key)) {
      return entry.value;
    }
    for (int i = 1; i < numberOfGenerations; ++i) {
      entry = cache[++cacheIndex];
      if (entry != null && entry.key.equals(key)) {
        final CacheEntry<K, V> temp = cache[cacheIndex - 1];
        cache[cacheIndex - 1] = entry;
        cache[cacheIndex] = temp;
        return entry.value;
      }
    }
    return null;
  }

  @Override
  public V getObject(@NotNull final K key) {
    int cacheIndex = CacheUtil.indexFor(key.hashCode(), generationSize, mask) * numberOfGenerations;
    for (int i = 0; i < numberOfGenerations; ++i, ++cacheIndex) {
      final CacheEntry<K, V> entry = cache[cacheIndex];
      if (entry != null && entry.key.equals(key)) {
        return entry.value;
      }
    }
    return null;
  }

  @Override
  public int count() {
    throw new UnsupportedOperationException();
  }

  @Override
  public CriticalSection newCriticalSection() {
    return TRIVIAL_CRITICAL_SECTION;
  }

  private static class CacheEntry<K, V> {

    @NotNull
    private final K key;
    @Nullable
    private V value;

    private CacheEntry(@NotNull final K key, @Nullable final V value) {
      this.key = key;
      this.value = value;
    }
  }
}
