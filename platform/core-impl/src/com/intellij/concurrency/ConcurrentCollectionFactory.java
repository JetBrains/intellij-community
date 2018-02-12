/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public class ConcurrentCollectionFactory {
  @NotNull
  @Contract(pure=true)
  public static <T, V> ConcurrentMap<T, V> createMap(@NotNull TObjectHashingStrategy<T> hashStrategy) {
    return new ConcurrentHashMap<>(hashStrategy);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> ConcurrentMap<T, V> createMap(int initialCapacity,
                                                     float loadFactor,
                                                     int concurrencyLevel,
                                                     @NotNull TObjectHashingStrategy<T> hashStrategy) {
    return new ConcurrentHashMap<>(initialCapacity,loadFactor,concurrencyLevel,hashStrategy);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Set<T> createConcurrentSet(@NotNull TObjectHashingStrategy<T> hashStrategy) {
    return Collections.newSetFromMap(createMap(hashStrategy));
  }
}
