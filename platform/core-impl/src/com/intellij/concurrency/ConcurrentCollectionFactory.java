// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.concurrency;

import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Creates various concurrent collections (e.g maps, sets) which can be customized with {@link TObjectHashingStrategy}
 */
public final class ConcurrentCollectionFactory {
  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> createConcurrentIdentityMap() {
    //noinspection unchecked
    return new ConcurrentHashMap<>((TObjectHashingStrategy<K>)TObjectHashingStrategy.IDENTITY);
  }

  @Contract(pure = true)
  public static @NotNull <T, V> ConcurrentMap<T, V> createMap(@NotNull TObjectHashingStrategy<T> hashStrategy) {
    return new ConcurrentHashMap<>(hashStrategy);
  }

  @Contract(pure = true)
  public static @NotNull <T, V> ConcurrentMap<T, V> createMap(int initialCapacity,
                                                              float loadFactor,
                                                              int concurrencyLevel,
                                                              @NotNull TObjectHashingStrategy<T> hashStrategy) {
    return new ConcurrentHashMap<>(initialCapacity,loadFactor,concurrencyLevel,hashStrategy);
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<T> createConcurrentSet(@NotNull TObjectHashingStrategy<T> hashStrategy) {
    return Collections.newSetFromMap(createMap(hashStrategy));
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<T> createConcurrentSet(int initialCapacity, @NotNull TObjectHashingStrategy<T> hashStrategy) {
    return Collections.newSetFromMap(createMap(initialCapacity, 0.75f, 16, hashStrategy));
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<T> createConcurrentSet(int initialCapacity,
                                                        float loadFactor,
                                                        int concurrencyLevel,
                                                        @NotNull TObjectHashingStrategy<T> hashStrategy) {
    return Collections.newSetFromMap(createMap(initialCapacity, loadFactor, concurrencyLevel, hashStrategy));
  }
}
