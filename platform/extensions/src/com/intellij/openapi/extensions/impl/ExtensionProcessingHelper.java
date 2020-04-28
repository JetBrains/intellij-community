// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.progress.ProcessCanceledException;
import gnu.trove.THashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap.SimpleImmutableEntry;
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
  public static <T> void forEachExtensionSafe(@NotNull Iterable<? extends T> iterable, @NotNull Consumer<? super T> extensionConsumer) {
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

  /**
   * See {@link com.intellij.openapi.extensions.ExtensionPointName#getByGroupingKey}.
   */
  public static <@NotNull K, @NotNull T> @NotNull List<T> getByGroupingKey(@NotNull ExtensionPointImpl<T> point,
                                                                           @NotNull K key,
                                                                           @NotNull Function<@NotNull T, @Nullable K> keyMapper) {
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
   * See {@link com.intellij.openapi.extensions.ExtensionPointName#getByKey}.
   */
  @ApiStatus.Internal
  public static <@NotNull K, @NotNull T, @NotNull V> @Nullable V getByKey(@NotNull ExtensionPointImpl<T> point,
                                                                          @NotNull K key,
                                                                          @NotNull Function<@NotNull T, @Nullable K> keyMapper,
                                                                          @NotNull Function<@NotNull T, @Nullable V> valueMapper) {
    SimpleImmutableEntry<Function<T, K>, Function<T, V>> cacheKey = new SimpleImmutableEntry<>(keyMapper, valueMapper);
    return doGetByKey(point, cacheKey, key, keyMapper, valueMapper, point.getCacheMap());
  }

  /**
   * See {@link com.intellij.openapi.extensions.ExtensionPointName#getByKey}.
   */
  @ApiStatus.Internal
  public static <@NotNull K, @NotNull T> @Nullable T getByKey(@NotNull ExtensionPointImpl<T> point,
                                                              @NotNull K key,
                                                              @NotNull Function<@NotNull T, @Nullable K> keyMapper) {
    return doGetByKey(point, keyMapper, key, keyMapper, Function.identity(), point.getCacheMap());
  }

  /**
   * See {@link com.intellij.openapi.extensions.ExtensionPointName#getByKey}.
   */
  @ApiStatus.Internal
  public static <@NotNull K, @NotNull T, @NotNull V> @NotNull V computeIfAbsent(@NotNull ExtensionPointImpl<T> point,
                                                                                @NotNull K key,
                                                                                @NotNull Function<K, V> valueProducer) {
    // Or to have double look-up (map for valueProducer, map for key), or using of composite key. Java GC is quite good, so, composite key.
    ConcurrentMap<SimpleImmutableEntry<K, Function<K, V>>, V> cache = point.getCacheMap();
    return cache.computeIfAbsent(new SimpleImmutableEntry<>(key, valueProducer), entry -> valueProducer.apply(entry.getKey()));
  }

  private static <CACHE_KEY, K, T, V> @Nullable V doGetByKey(@NotNull ExtensionPointImpl<T> point,
                                                             @NotNull CACHE_KEY cacheKey,
                                                             @NotNull K key,
                                                             @NotNull Function<T, K> keyMapper,
                                                             @NotNull Function<@NotNull T, @Nullable V> valueMapper,
                                                             @NotNull ConcurrentMap<CACHE_KEY, Map<K, V>> keyMapperToCache) {
    Map<K, V> cache = keyMapperToCache.get(cacheKey);
    if (cache == null) {
      cache = buildCacheForKeyMapper(keyMapper, valueMapper, point);
      Map<K, V> prev = keyMapperToCache.putIfAbsent(cacheKey, cache);
      if (prev != null) {
        cache = prev;
      }
    }
    return cache.get(key);
  }

  private static <K, T> @NotNull Map<K, List<T>> buildCacheForGroupingKeyMapper(@NotNull Function<T, K> keyMapper,
                                                                                @NotNull ExtensionPointImpl<T> point) {
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

  private static @NotNull <K, T, V> Map<K, V> buildCacheForKeyMapper(@NotNull Function<T, K> keyMapper,
                                                                     @NotNull Function<@NotNull T, @Nullable V> valueMapper,
                                                                     @NotNull ExtensionPointImpl<T> point) {
    List<T> extensions = point.getExtensionList();
    Map<K, V> cache = new THashMap<>(extensions.size());
    for (T extension : extensions) {
      K key = keyMapper.apply(extension);
      if (key == null) {
        continue;
      }

      V value = valueMapper.apply(extension);
      if (value == null) {
        continue;
      }
      cache.put(key, value);
    }
    return cache;
  }
}
