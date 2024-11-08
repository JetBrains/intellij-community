// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.extensions.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.annotations.ApiStatus
import java.util.AbstractMap.SimpleImmutableEntry
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentMap
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier

// Separate class to keep ExtensionPointImpl class implementation clear and readable,
// such simple util code better to keep separately.
// getThreadSafeAdapterList can be opened to use here directly to avoid using of iterator, but it doesn't make sense
// - if there is already a cached extension list, it will be used instead of custom iterator.
// It is not only about performance and common sense,
// but also supporting the ability to mock an extension list in tests (a custom list is set).
@ApiStatus.Internal
object ExtensionProcessingHelper {
  internal fun <T : Any> findFirstSafe(predicate: Predicate<in T>, sequence: Sequence<T>): T? {
    return computeSafeIfAny(processor = { if (predicate.test(it)) it else null }, sequence = sequence)
  }

  internal inline fun <T : Any, R> computeSafeIfAny(processor: (T) -> R, sequence: Sequence<T>): R? {
    for (t in sequence) {
      try {
        processor(t)?.let {
          return it
        }
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        if (e::class.java.name != "com.intellij.openapi.project.IndexNotReadyException") {
          logger<ExtensionPointImpl<*>>().error(e)
        }
      }
    }
    return null
  }

  /**
   * See [com.intellij.openapi.extensions.ExtensionPointName.getByGroupingKey].
   */
  fun <K : Any, T : Any> getByGroupingKey(point: ExtensionPointImpl<T>,
                                          cacheId: Class<*>,
                                          key: K,
                                          keyMapper: Function<T, K?>): List<T> {
    val keyMapperToCache = point.getCacheMap<Class<*>, Map<K, MutableList<T>>>()
    var cache = keyMapperToCache.get(cacheId)
    if (cache == null) {
      cache = buildCacheForGroupingKeyMapper(keyMapper, point)
      val prev = keyMapperToCache.putIfAbsent(cacheId, cache)
      if (prev != null) {
        cache = prev
      }
    }
    return cache.get(key) ?: persistentListOf()
  }

  /**
   * See [com.intellij.openapi.extensions.ExtensionPointName.getByKey].
   */
  @ApiStatus.Internal
  fun <K : Any, T : Any, V : Any?> getByKey(point: ExtensionPointImpl<T>,
                                            key: K,
                                            cacheId: Class<*>,
                                            keyMapper: Function<T, K?>,
                                            valueMapper: Function<T, V>): V? {
    return doGetByKey(point = point,
                      cacheId = cacheId,
                      key = key,
                      keyMapper = keyMapper,
                      valueMapper,
                      point.getCacheMap())
  }

  /**
   * See [com.intellij.openapi.extensions.ExtensionPointName.getByKey].
   */
  internal fun <K : Any, T : Any> getByKey(point: ExtensionPointImpl<T>,
                                           key: K,
                                           cacheId: Class<*>,
                                           keyMapper: Function<T, K?>): T? {
    return doGetByKey(point = point,
                      cacheId = cacheId,
                      key = key,
                      keyMapper = keyMapper,
                      valueMapper = Function.identity(),
                      keyMapperToCache = point.getCacheMap())
  }

  /**
   * See [com.intellij.openapi.extensions.ExtensionPointName.getByKey].
   */
  internal fun <K : Any, T : Any, V : Any> computeIfAbsent(point: ExtensionPointImpl<T>,
                                                           key: K,
                                                           cacheId: Class<*>,
                                                           valueProducer: Function<K, V>): V {
    // Or to have a double look-up (map for valueProducer, map for a key), or using of a composite key.
    // Java GC is quite good, so, composite key.
    val cache = point.getCacheMap<SimpleImmutableEntry<K, Class<*>>, V>()
    return cache.computeIfAbsent(SimpleImmutableEntry(key, cacheId)) { (key1): SimpleImmutableEntry<K, Class<*>> ->
      valueProducer.apply(key1)
    }
  }

  internal fun <T : Any, V : Any> computeIfAbsent(point: ExtensionPointImpl<T>,
                                                  cacheId: Class<*>,
                                                  valueProducer: Supplier<V>): V {
    val cache = point.getCacheMap<Class<*>, V>()
    return cache.computeIfAbsent(cacheId) { valueProducer.get() }
  }

  private fun <CACHE_KEY : Any, K : Any, T : Any, V : Any?> doGetByKey(point: ExtensionPoint<T>,
                                                                      cacheId: CACHE_KEY,
                                                                      key: K,
                                                                      keyMapper: Function<T, K?>,
                                                                      valueMapper: Function<T, V>,
                                                                      keyMapperToCache: ConcurrentMap<CACHE_KEY, Map<K, V?>>): V? {
    var cache = keyMapperToCache.get(cacheId)
    if (cache == null) {
      cache = buildCacheForKeyMapper(keyMapper, valueMapper, point)
      val prev = keyMapperToCache.putIfAbsent(cacheId, cache)
      if (prev != null) {
        cache = prev
      }
    }
    return cache.get(key)
  }

  private fun <K : Any, T : Any> buildCacheForGroupingKeyMapper(keyMapper: Function<T, K?>,
                                                                point: ExtensionPoint<T>): Map<K, MutableList<T>> {
    // use HashMap instead of THashMap - a lot of keys not expected;
    // nowadays HashMap is more optimized (e.g., computeIfAbsent implemented in an efficient manner)
    val cache = HashMap<K, MutableList<T>>()
    for (extension in point.extensionList) {
      val key = keyMapper.apply(extension) ?: continue
      // SmartList is not used - expected that list size will be > 1
      cache.computeIfAbsent(key) { ArrayList() }.add(extension)
    }
    return cache
  }

  private fun <K : Any, T : Any, V> buildCacheForKeyMapper(keyMapper: Function<T, K?>,
                                                           valueMapper: Function<T, V>,
                                                           point: ExtensionPoint<T>): Map<K, V> {
    val extensions = point.extensionList
    val cache = HashMap<K, V>(extensions.size)
    for (extension in extensions) {
      val key: K = keyMapper.apply(extension) ?: continue
      val value: V = valueMapper.apply(extension) ?: continue
      cache.put(key, value)
    }
    return cache
  }
}