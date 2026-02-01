// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe async cache that deduplicates concurrent requests for the same key.
 *
 * **Important**: Both successful values AND failures are cached permanently.
 * If a loader throws an exception, that failed computation is cached, and all later
 * calls for the same key will receive the same exception without retrying.
 *
 * This prevents expensive repeated computations and thundering herd scenarios when
 * operations fail.
 */
class AsyncCache<K : Any, V>(private val scope: CoroutineScope) {
  private val cache = ConcurrentHashMap<K, Any>()

  @Suppress("UNCHECKED_CAST")
  suspend fun getOrPut(key: K, loader: suspend () -> V): V {
    while (true) {
      when (val existing = cache.get(key)) {
        is CachedValue<*> -> {
          return existing.value as V
        }
        is Deferred<*> -> {
          return (existing as Deferred<V>).await()
        }
        else -> {
          lateinit var deferred: Deferred<V>
          // Use LAZY start to prevent race condition with lateinit
          deferred = scope.async(start = CoroutineStart.LAZY) {
            val value = loader()
            // only replace it if OUR deferred is still in cache (atomic, prevents overwriting winner's result)
            cache.replace(key, deferred, CachedValue(value))
            value
          }

          val winner = cache.putIfAbsent(key, deferred)
          if (winner == null) {
            return deferred.await()
          }
          else {
            deferred.cancel()
          }
        }
      }
    }
  }

  /**
   * Closes the cache by processing all completed values and cancelling pending computations.
   * Each entry is atomically removed before processing.
   */
  @Suppress("UNCHECKED_CAST")
  fun close(action: (V) -> Unit) {
    val iterator = cache.keys.iterator()
    while (iterator.hasNext()) {
      val key = iterator.next()
      when (val entry = cache.remove(key)) {
        is CachedValue<*> -> action(entry.value as V)
        is Deferred<*> -> entry.cancel()
      }
    }
  }

  /**
   * Iterates over all completed cache entries (key-value pairs).
   * Skips entries that are still computing (Deferred not yet completed).
   */
  @Suppress("UNCHECKED_CAST")
  fun forEachCompleted(action: (key: K, value: V) -> Unit) {
    for ((key, entry) in cache) {
      if (entry is CachedValue<*>) {
        action(key, entry.value as V)
      }
    }
  }

  private class CachedValue<V>(@JvmField val value: V)
}
