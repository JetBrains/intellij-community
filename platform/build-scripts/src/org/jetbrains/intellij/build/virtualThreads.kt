// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * A dispatcher with one virtual thread per resume.
 *
 * A fork body runs its coroutines here, so no CPU work of the build reaches the kotlinx scheduler.
 */
internal val buildVirtualThreadDispatcher: CoroutineDispatcher =
  Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("build-", 0).factory()).asCoroutineDispatcher()

/**
 * What a group of forks does when one of them fails.
 */
@ApiStatus.Internal
enum class VirtualThreadTaskPolicy {
  /** The first failure cancels the other forks. This is the behaviour of `coroutineScope`. */
  FAIL_FAST,

  /** Every fork runs to its end. The first failure is thrown with the others suppressed. This is the behaviour of `supervisorScope`. */
  RUN_ALL,
}

/**
 * Runs [block] on a new virtual thread, and returns the future of its result.
 *
 * The body sees the OpenTelemetry context of the caller and a [CoroutineName] of [name]. Its coroutines run on
 * [buildVirtualThreadDispatcher]. A cancelled future interrupts the body thread, and the interrupt cancels the body.
 */
@ApiStatus.Internal
fun <T> forkOnVirtualThread(name: String, block: suspend CoroutineScope.() -> T): CompletableFuture<T> {
  val future = CompletableFuture<T>()
  val telemetryContext = Context.current()
  val thread = Thread.ofVirtual().name(name).unstarted {
    try {
      val value = runBlocking(buildVirtualThreadDispatcher + CoroutineName(name) + telemetryContext.asContextElement()) {
        block()
      }
      future.complete(value)
    }
    catch (e: Throwable) {
      future.completeExceptionally(e)
    }
  }
  future.whenComplete { _, _ ->
    if (future.isCancelled) {
      thread.interrupt()
    }
  }
  thread.start()
  return future
}

/**
 * A group of forks that [virtualThreadTasks] joins when its block ends.
 *
 * It replaces `coroutineScope` with `async` and `launch` children. A fork is a virtual thread, and the group holds no
 * queue, so a scheduler cannot lose a fork.
 */
@ApiStatus.Internal
class VirtualThreadTasks internal constructor(private val policy: VirtualThreadTaskPolicy) {
  private val futures = CopyOnWriteArrayList<CompletableFuture<*>>()

  /** Starts [block] on a new virtual thread. See [forkOnVirtualThread]. */
  fun <T> fork(name: String, block: suspend CoroutineScope.() -> T): CompletableFuture<T> {
    val future = forkOnVirtualThread(name, block)
    futures.add(future)
    if (policy == VirtualThreadTaskPolicy.FAIL_FAST) {
      future.whenComplete { _, e ->
        if (e != null && !future.isCancelled) {
          cancelAll()
        }
      }
    }
    return future
  }

  internal fun cancelAll() {
    for (future in futures) {
      future.cancel(true)
    }
  }

  /**
   * Waits for every fork, including a fork that another fork adds meanwhile.
   *
   * Throws the first failure with the other failures suppressed. A cancelled fork counts as a failure only when no
   * fork failed on its own. When the caller is cancelled, every fork is cancelled.
   */
  internal suspend fun joinAll() {
    while (true) {
      val pending = futures.filter { !it.isDone }
      if (pending.isEmpty()) {
        break
      }
      try {
        CompletableFuture.allOf(*pending.toTypedArray()).await()
      }
      catch (e: CancellationException) {
        // `allOf` also throws this when a fork was cancelled, so only the caller's own cancellation ends the wait
        if (!currentCoroutineContext().isActive) {
          cancelAll()
          throw e
        }
      }
      catch (_: Throwable) {
        // a fork failed; the loop below reports it after every fork ended
      }
    }

    var failure: Throwable? = null
    var cancellation: Throwable? = null
    for (future in futures) {
      if (future.isCancelled) {
        if (cancellation == null) {
          cancellation = runCatching { future.join() }.exceptionOrNull()
        }
        continue
      }
      if (!future.isCompletedExceptionally) {
        continue
      }
      val e = future.exceptionNow()
      if (failure == null) {
        failure = e
      }
      else {
        failure.addSuppressed(e)
      }
    }
    (failure ?: cancellation)?.let { throw it }
  }
}

/**
 * Runs [block] with a group of forks, and waits for every fork before it returns.
 *
 * A failure of [block] cancels the forks and is rethrown. A failure of a fork follows [policy]. This is the
 * replacement for `coroutineScope` and `supervisorScope` in the packaging pipeline.
 */
@ApiStatus.Internal
suspend fun <T> virtualThreadTasks(
  policy: VirtualThreadTaskPolicy = VirtualThreadTaskPolicy.FAIL_FAST,
  block: suspend VirtualThreadTasks.() -> T,
): T {
  val tasks = VirtualThreadTasks(policy)
  val result = try {
    tasks.block()
  }
  catch (e: Throwable) {
    tasks.cancelAll()
    runCatching { tasks.joinAll() }
    throw e
  }
  tasks.joinAll()
  return result
}
