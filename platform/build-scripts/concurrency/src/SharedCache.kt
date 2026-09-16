@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.concurrency

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration

/** Shares each load within [owner]. Closing waits for loaders before disposing their successful results. */
@ApiStatus.Internal
class SharedCache<K : Any, V>(
  val owner: SharedTaskOwner,
  private val dispose: (V) -> Unit = {},
) : AutoCloseable {
  private val entries = HashMap<K, Entry<V>>()
  private val closed = CompletableFuture<Unit>()
  private var closing: Thread? = null

  init {
    owner.onClose(this)
  }

  /** A waiter timeout or interruption leaves the shared computation unchanged. */
  fun getOrPut(key: K, timeout: Duration? = null, loader: () -> V): V {
    val entry = synchronized(entries) {
      checkOpen()
      val existing = entries.get(key)
      val failure = existing?.computation?.result?.failureOrNull()
      if (existing != null && failure !is CancellationException && failure !is InterruptedException) existing
      else {
        val token = Any()
        Entry(token, owner.start("${owner.name}: $key", token) {
          withSingleFlightOwners(currentSingleFlightOwners(), this, loader)
        }).also { entries.put(key, it) }
      }
    }
    checkRecursiveSingleFlightAwait(entry.token, "SharedCache entry for key '$key'", entry.computation.result.isDone)
    val value = awaitTask("${owner.name}: $key") { entry.computation.await(timeout) }
    synchronized(entries) { checkOpen() }
    return value
  }

  private fun checkOpen() {
    owner.checkOpen()
    check(closing == null) { "The shared cache is closed" }
  }

  override fun close() {
    checkRecursiveSingleFlightAwait(this, "closing a shared cache", completed = false)
    if (closed.isDone) {
      closed.awaitUninterruptibly()
      return
    }
    val values = synchronized(entries) {
      check(closing !== Thread.currentThread()) { "A cache cannot close itself recursively" }
      if (closing != null) null
      else {
        closing = Thread.currentThread()
        entries.values.toList().also { entries.clear() }
      }
    }
    if (values == null) {
      closed.awaitUninterruptibly()
      return
    }
    var interrupted = Thread.interrupted()
    var failure: Throwable? = null
    try {
      for (entry in values) entry.computation.cancel()
      for (entry in values) {
        interrupted = joinUninterruptibly(entry.computation.thread) || interrupted
      }
      for (entry in values) {
        val result = entry.computation.result
        if (result.isCompletedExceptionally) continue
        try {
          dispose(result.resultNow())
        }
        catch (error: Throwable) {
          failure = accumulateFailure(failure, error)
        }
      }
    }
    finally {
      if (failure == null) closed.complete(Unit) else closed.completeExceptionally(failure)
      if (interrupted) Thread.currentThread().interrupt()
    }
    failure?.let { throw it }
  }

  private class Entry<T>(val token: Any, val computation: SharedComputation<T>)
}

/** A value loaded once within an explicit shared owner. */
@ApiStatus.Internal
class SharedLazy<T>(owner: SharedTaskOwner, name: String, private val initializer: () -> T) {
  private val cache = SharedCache<String, T>(owner)
  private val key = name

  fun get(): T = cache.getOrPut(key, loader = initializer)
}
