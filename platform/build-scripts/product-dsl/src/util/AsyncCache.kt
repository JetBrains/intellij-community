// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
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
 * **Important**: Successful values and non-cancellation failures are cached permanently.
 * If a loader throws a non-cancellation exception, that failed computation is cached,
 * and all later calls for the same key will receive the same exception without retrying.
 *
 * Cancellation is treated as an aborted attempt rather than a cacheable result.
 * If the owning caller is canceled, the in-flight entry is evicted and the next lookup retries.
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
          val result = CompletableDeferred<V>()
          lateinit var entry: CacheEntry<V>
          @Suppress("RAW_SCOPE_CREATION")
          entry = CacheEntry(
            result = result,
            owner = owner,
            computation = CoroutineScope(currentContext + singleFlightComputationContext(currentContext, owner))
              .launch(start = CoroutineStart.LAZY) {
                try {
                  val value = loader()
                  result.complete(value)
                  cache.replace(key, entry, CachedValue(value))
                }
                catch (e: Throwable) {
                  if (e is CancellationException) {
                    cache.remove(key, entry)
                  }
                  result.completeExceptionally(e)
                }
              },
          )

          if (cache.putIfAbsent(key, entry) == null) {
            entry.computation.start()
            return entry.result.await()
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
}

private inline fun <V> processPendingEntry(entry: CacheEntry<V>, action: (V) -> Unit) {
  if (!processCompletedEntry(entry, action)) {
    entry.computation.cancel()
    entry.result.cancel()
  }
}

private inline fun <V> processCompletedEntry(entry: CacheEntry<V>, action: (V) -> Unit): Boolean {
  if (entry.result.isCompleted && entry.result.getCompletionExceptionOrNull() == null) {
    action(entry.result.getCompleted())
    return true
  }
  return false
}

private class CacheEntry<V>(
  @JvmField val result: CompletableDeferred<V>,
  @JvmField val owner: Any,
  @JvmField val computation: Job,
)

private class CachedValue<V>(@JvmField val value: V)
