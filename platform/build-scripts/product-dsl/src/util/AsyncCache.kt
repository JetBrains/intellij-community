// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.intellij.build.checkRecursiveSingleFlightAwait
import org.jetbrains.intellij.build.singleFlightComputationContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe async cache that deduplicates concurrent requests for the same key.
 *
 * **Important**: Successful values and non-cancellation failures are cached permanently.
 * If a loader throws a non-cancellation exception, that failed computation is cached,
 * and all later calls for the same key will receive the same exception without retrying.
 *
 * A computation is not a child of the caller that started it. Once started, it runs to completion, and every caller
 * only waits for it. A caller that is cancelled, before or after the computation was dispatched, stops waiting and
 * changes nothing for the other callers. Only [close], or a loader that throws [CancellationException] itself,
 * cancels a computation. Such an entry is evicted, and the next lookup retries.
 *
 * The computation inherits the context of the caller without its [Job], so it runs on the dispatcher of the caller.
 *
 * This prevents expensive repeated computations and thundering herd scenarios when
 * operations fail.
 */
class AsyncCache<K : Any, V> {
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
          if (entry.result.isCompleted && entry.result.getCompletionExceptionOrNull() is CancellationException) {
            cache.remove(key, entry)
            continue
          }
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
          @Suppress("RAW_SCOPE_CREATION")
          val computation = CoroutineScope(currentContext.minusKey(Job) + singleFlightComputationContext(currentContext, owner))
            .async(start = CoroutineStart.LAZY) { loader() }
          val entry = CacheEntry(result = computation, owner = owner)
          if (cache.putIfAbsent(key, entry) == null) {
            computation.invokeOnCompletion { cause ->
              // a failure stays in the map, so every later caller gets the same exception without a retry
              when (cause) {
                null -> cache.replace(key, entry, CachedValue(computation.getCompleted()))
                is CancellationException -> cache.remove(key, entry)
                else -> {}
              }
            }
            computation.start()
            return computation.await()
          }
          else {
            computation.cancel()
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
}

private inline fun <V> processPendingEntry(entry: CacheEntry<V>, action: (V) -> Unit) {
  val result = entry.result
  if (result.isCompleted && result.getCompletionExceptionOrNull() == null) {
    action(result.getCompleted())
  }
  else {
    result.cancel()
  }
}

private class CacheEntry<V>(
  @JvmField val result: Deferred<V>,
  @JvmField val owner: Any,
)

private class CachedValue<V>(@JvmField val value: V)
