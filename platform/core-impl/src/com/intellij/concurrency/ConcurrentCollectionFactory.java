// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.concurrency;

import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Creates various concurrent collections (e.g maps, sets) which can be customized with {@link HashingStrategy}
 */
public final class ConcurrentCollectionFactory {
  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentIdentityMap() {
    return new ConcurrentHashMap<>(HashingStrategy.identity());
  }

  @Contract(pure = true)
  public static @NotNull <T, V> ConcurrentMap<T, V> createMap(@NotNull HashingStrategy<T> hashStrategy) {
    return new ConcurrentHashMap<>(hashStrategy);
  }

  @Contract(pure = true)
  public static @NotNull <T, V> ConcurrentMap<T, V> createMap() {
    return new ConcurrentHashMap<>(HashingStrategy.canonical());
  }

  @Contract(pure = true)
  public static @NotNull <T, V> ConcurrentMap<T, V> createMap(int initialCapacity,
                                                              float loadFactor,
                                                              int concurrencyLevel,
                                                              @NotNull HashingStrategy<T> hashStrategy) {
    return new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel, hashStrategy);
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<T> createConcurrentSet(@NotNull HashingStrategy<T> hashStrategy) {
    return Collections.newSetFromMap(createMap(hashStrategy));
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<T> createConcurrentIdentitySet() {
    return Collections.newSetFromMap(createMap(HashingStrategy.identity()));
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<T> createConcurrentIdentitySet(int initialCapacity) {
    return Collections.newSetFromMap(createMap(initialCapacity, 0.75f, 16, HashingStrategy.identity()));
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<T> createConcurrentSet(int initialCapacity, @NotNull HashingStrategy<T> hashStrategy) {
    return Collections.newSetFromMap(createMap(initialCapacity, 0.75f, 16, hashStrategy));
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<T> createConcurrentSet(int initialCapacity,
                                                        float loadFactor,
                                                        int concurrencyLevel,
                                                        @NotNull HashingStrategy<T> hashStrategy) {
    return Collections.newSetFromMap(createMap(initialCapacity, loadFactor, concurrencyLevel, hashStrategy));
  }
}
