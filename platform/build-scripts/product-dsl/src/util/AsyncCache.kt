// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import org.jetbrains.intellij.build.checkRecursiveSingleFlightAwait
import org.jetbrains.intellij.build.singleFlightComputationContext
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
class AsyncCache<K : Any, V>(scope: CoroutineScope) {
  private val defaultParentJob = scope.coroutineContext[Job]
  private val cache = ConcurrentHashMap<K, Any>()

  @Suppress("UNCHECKED_CAST")
  suspend fun getOrPut(key: K, loader: suspend () -> V): V {
    val currentContext = currentCoroutineContext()
    while (true) {
      when (val existing = cache.get(key)) {
        is CachedValue<*> -> {
          return existing.value as V
        }
        is CacheEntry<*> -> {
          val entry = existing as CacheEntry<V>
          checkRecursiveSingleFlightAwait(
            currentContext = currentContext,
            owner = entry.owner,
            operationName = "AsyncCache entry for key '$key'",
            deferred = entry.result,
          )
          return entry.result.await()
        }
        else -> {
          val owner = Any()
          val result = CompletableDeferred<V>(currentContext[Job] ?: defaultParentJob)
          @Suppress("RAW_SCOPE_CREATION")
          val entry = CacheEntry(
            result = result,
            owner = owner,
            computation = CoroutineScope(currentContext.minusKey(Job) + result + singleFlightComputationContext(currentContext, owner))
              .launch(start = CoroutineStart.LAZY) {
                try {
                  result.complete(loader())
                }
                catch (t: Throwable) {
                  result.completeExceptionally(t)
                }
              },
          )

          if (cache.putIfAbsent(key, entry) == null) {
            entry.computation.start()
            val value = entry.result.await()
            cache.replace(key, entry, CachedValue(value))
            return value
          }
          else {
            entry.computation.cancel()
            result.cancel()
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
        is CacheEntry<*> -> processPendingEntry(entry as CacheEntry<V>, action)
      }
    }
  }

  /**
   * Iterates over all completed cache entries (key-value pairs).
   * Skips entries that are still computing (Deferred not yet completed).
   */
  @Suppress("UNCHECKED_CAST", "unused")
  fun forEachCompleted(action: (key: K, value: V) -> Unit) {
    for ((key, entry) in cache) {
      when (entry) {
        is CachedValue<*> -> action(key, entry.value as V)
        is CacheEntry<*> -> processCompletedEntry(entry as CacheEntry<V>) { value -> action(key, value) }
      }
    }
  }

  private fun processPendingEntry(entry: CacheEntry<V>, action: (V) -> Unit) {
    if (!processCompletedEntry(entry, action)) {
      entry.computation.cancel()
      entry.result.cancel()
    }
  }

  private fun processCompletedEntry(entry: CacheEntry<V>, action: (V) -> Unit): Boolean {
    if (entry.result.isCompleted && entry.result.getCompletionExceptionOrNull() == null) {
      action(entry.result.getCompleted())
      return true
    }
    return false
  }

  private class CacheEntry<V>(
    val result: CompletableDeferred<V>,
    val owner: Any,
    val computation: Job,
  )

  private class CachedValue<V>(@JvmField val value: V)
}
