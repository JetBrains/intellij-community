// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.util

import kotlinx.coroutines.CancellationException
import org.jetbrains.intellij.build.checkRecursiveSingleFlightAwait
import org.jetbrains.intellij.build.failureOrNull
import org.jetbrains.intellij.build.joinShared
import org.jetbrains.intellij.build.startSharedComputation
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Thread-safe cache that deduplicates concurrent requests for the same key.
 *
 * **Important**: Successful values and non-cancellation failures are cached permanently.
 * If a loader throws a non-cancellation exception, that failed computation is cached,
 * and all later calls for the same key will receive the same exception without retrying.
 *
 * A computation runs on its own virtual thread and is not a child of the caller that started it. Once started, it
 * runs to completion, and every caller only waits for it. A caller that stops waiting, which only a timeout does,
 * changes nothing for the other callers. Only [close], or a loader that throws [CancellationException] itself,
 * cancels a computation. Such an entry is evicted, and the next lookup retries.
 *
 * The loader runs inside the single-flight computations of the caller, so a recursive [getOrPut] of the same key
 * fails fast.
 *
 * The loader runs on a thread of its own and carries no telemetry context. A loader that opens a span passes the
 * context of its first caller into the entry it runs. See `runBlockingOnVirtualThreads`.
 *
 * This prevents expensive repeated computations and thundering herd scenarios when
 * operations fail.
 */
class AsyncCache<K : Any, V> {
  private val cache = ConcurrentHashMap<K, Any>()

  /**
   * Returns the value of [key], and computes it with [loader] when no other caller has started it.
   *
   * A `null` [timeout] waits for as long as the computation takes. A [timeout] throws
   * [java.util.concurrent.TimeoutException] and leaves the computation running, so a later caller reuses it.
   */
  @Suppress("UNCHECKED_CAST")
  fun getOrPut(key: K, timeout: Duration? = null, loader: () -> V): V {
    // the hot path of a hit, so it allocates no future
    (cache.get(key) as? CachedValue<V>)?.let {
      return it.value
    }
    return sharedFuture(key, loader).await(timeout)
  }

  /**
   * The future of the shared computation of [key], for a caller that must wait without blocking its thread.
   *
   * A coroutine waits on it with `awaitShared` and stays cancellable. Every other caller uses [getOrPut].
   */
  @Suppress("UNCHECKED_CAST")
  fun sharedFuture(key: K, loader: () -> V): CompletableFuture<V> {
    while (true) {
      when (val existing = cache.get(key)) {
        is CachedValue<*> -> {
          return CompletableFuture.completedFuture(existing.value as V)
        }
        is CacheEntry<*> -> {
          val entry = existing as CacheEntry<V>
          if (entry.result.isDone && entry.result.failureOrNull() is CancellationException) {
            cache.remove(key, entry)
            continue
          }
          checkRecursiveSingleFlightAwait(
            owner = entry.owner,
            operationName = "AsyncCache entry for key '$key'",
            completed = entry.result.isDone,
          )
          return entry.result
        }
        else -> {
          // the mapping function only starts a thread, so it is short under the lock of the map
          val owner = Any()
          val entry = cache.computeIfAbsent(key) {
            CacheEntry(result = startSharedComputation(name = "AsyncCache: $key", owner = owner, block = loader), owner = owner)
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
          return entry.result
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

/**
 * A timeout waits on a copy, so it never completes the shared future and never poisons the entry for the others.
 */
private fun <T> CompletableFuture<T>.await(timeout: Duration?): T {
  return if (timeout == null) joinShared() else copy().orTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS).joinShared()
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

/** [result] is the future of the computation that runs the loader. */
private class CacheEntry<V>(
  @JvmField val result: CompletableFuture<V>,
  @JvmField val owner: Any,
)

private class CachedValue<V>(@JvmField val value: V)
