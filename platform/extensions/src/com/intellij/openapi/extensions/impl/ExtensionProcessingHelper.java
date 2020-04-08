// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.progress.ProcessCanceledException;
import gnu.trove.THashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

// Separate class to keep ExtensionPointImpl class implementation clear and readable,
// such simple util code better to keep separately.

// getThreadSafeAdapterList can be opened to used here directly to avoid using of iterator, but it doesn't make sense
// - if there is already cached extension list, it will be used instead of custom iterator.
// It is not only about performance and common sense, but also supporting ability to mock extension list in tests (custom list is set).
@ApiStatus.Internal
public final class ExtensionProcessingHelper {
  public static <T> void forEachExtensionSafe(@NotNull Consumer<? super T> extensionConsumer, @NotNull Iterable<? extends T> iterable) {
    for (T t : iterable) {
      if (t == null) {
        break;
      }

      try {
        extensionConsumer.accept(t);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        ExtensionPointImpl.LOG.error(e);
      }
    }
  }

  public static @Nullable <T> T findFirstSafe(@NotNull Predicate<? super T> predicate, @NotNull Iterable<? extends T> iterable) {
    return computeSafeIfAny(o -> predicate.test(o) ? o : null, iterable);
  }

  public static @Nullable <T, R> R computeSafeIfAny(@NotNull Function<? super T, ? extends R> processor, @NotNull Iterable<? extends T> iterable) {
    for (T t : iterable) {
      if (t == null) {
        return null;
      }

      try {
        R result = processor.apply(t);
        if (result != null) {
          return result;
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        ExtensionPointImpl.LOG.error(e);
      }
    }

    return null;
  }

  public static @NotNull <@NotNull K, @NotNull T> List<T> getByGroupingKey(@NotNull ExtensionPointImpl<T> point, @NotNull K key, @NotNull Function<@NotNull T, @Nullable K> keyMapper) {
    ConcurrentMap<Function<T, K>, Map<K, List<T>>> keyMapperToCache = point.getCacheMap();
    Map<K, List<T>> cache = keyMapperToCache.get(keyMapper);
    if (cache == null) {
      cache = buildCacheForGroupingKeyMapper(keyMapper, point);
      Map<K, List<T>> prev = keyMapperToCache.putIfAbsent(keyMapper, cache);
      if (prev != null) {
        cache = prev;
      }
    }

    List<T> result = cache.get(key);
    return result == null ? Collections.emptyList() : result;
  }

  /**
   * To exclude extension from result, return null key.
   */
  public static <@NotNull K, @NotNull T> @Nullable T getByKey(@NotNull ExtensionPointImpl<T> point, @NotNull K key, @NotNull Function<@NotNull T, @Nullable K> keyMapper) {
    ConcurrentMap<Function<T, K>, Map<K, T>> keyMapperToCache = point.getCacheMap();
    Map<K, T> cache = keyMapperToCache.get(keyMapper);
    if (cache == null) {
      cache = buildCacheForKeyMapper(keyMapper, point);
      Map<K, T> prev = keyMapperToCache.putIfAbsent(keyMapper, cache);
      if (prev != null) {
        cache = prev;
      }
    }
    return cache.get(key);
  }

  private static  @NotNull <K, T> Map<K, List<T>> buildCacheForGroupingKeyMapper(@NotNull Function<T, K> keyMapper, @NotNull ExtensionPointImpl<T> point) {
    // use HashMap instead of THashMap - a lot of keys not expected, nowadays HashMap is a more optimized (e.g. computeIfAbsent implemented in an efficient manner)
    Map<K, List<T>> cache = new HashMap<>();
    for (T extension : point.getExtensionList()) {
      K key = keyMapper.apply(extension);
      if (key != null) {
        // SmartList is not used - expected that list size will be > 1
        cache.computeIfAbsent(key, k -> new ArrayList<>()).add(extension);
      }
    }
    return cache;
  }

  private static @NotNull <K, T> Map<K, T> buildCacheForKeyMapper(@NotNull Function<T, K> keyMapper, @NotNull ExtensionPointImpl<T> point) {
    List<T> extensions = point.getExtensionList();
    Map<K, T> cache = new THashMap<>(extensions.size());
    for (T extension : extensions) {
      K key = keyMapper.apply(extension);
      if (key != null) {
        cache.put(key, extension);
      }
    }
    return cache;
  }
}
