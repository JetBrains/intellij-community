// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.intellij.build.awaitShared
import org.jetbrains.intellij.build.checkRecursiveSingleFlightAwait
import org.jetbrains.intellij.build.failureOrNull
import org.jetbrains.intellij.build.forkOnVirtualThread
import org.jetbrains.intellij.build.singleFlightComputationContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe async cache that deduplicates concurrent requests for the same key.
 *
 * **Important**: Successful values and non-cancellation failures are cached permanently.
 * If a loader throws a non-cancellation exception, that failed computation is cached,
 * and all later calls for the same key will receive the same exception without retrying.
 *
 * A computation runs on its own virtual thread and is not a child of the caller that started it. Once started, it
 * runs to completion, and every caller only waits for it. A caller that is cancelled stops waiting and changes
 * nothing for the other callers. Only [close], or a loader that throws [CancellationException] itself, cancels
 * a computation. Such an entry is evicted, and the next lookup retries.
 *
 * The loader sees the single-flight context of the caller, so a recursive `getOrPut` of the same key fails fast.
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
          if (entry.result.isDone && entry.result.failureOrNull() is CancellationException) {
            cache.remove(key, entry)
            continue
          }
          checkRecursiveSingleFlightAwait(
            currentContext = currentContext,
            owner = entry.owner,
            operationName = "AsyncCache entry for key '$key'",
            completed = entry.result.isDone,
          )
          return entry.result.awaitShared()
        }
        else -> {
          // the mapping function only starts a coroutine, so it is short under the lock of the map
          val owner = Any()
          val entry = cache.computeIfAbsent(key) {
            val result = forkOnVirtualThread(name = "AsyncCache: $key", parentContext = currentContext + singleFlightComputationContext(currentContext, owner)) {
              loader()
            }
            CacheEntry(result = result, owner = owner)
          }
          if (entry !is CacheEntry<*> || entry.owner !== owner) {
            // another caller won the race; the next round of the loop reads its entry or its value
            continue
          }
          entry as CacheEntry<V>
          entry.result.whenComplete { value, e ->
            // a failure stays in the map, so every later caller gets the same exception without a retry
            when (e) {
              null -> cache.replace(key, entry, CachedValue(value))
              is CancellationException -> cache.remove(key, entry)
              else -> {}
            }
          }
          return entry.result.awaitShared()
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
  if (result.isDone && !result.isCompletedExceptionally) {
    action(result.resultNow())
  }
  else {
    result.cancel(false)
  }
}

/** [result] is the future of the fork that runs the loader. A cancel of it cancels the loader. */
private class CacheEntry<V>(
  @JvmField val result: CompletableFuture<V>,
  @JvmField val owner: Any,
)

private class CachedValue<V>(@JvmField val value: V)
