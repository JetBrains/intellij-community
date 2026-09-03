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
import kotlinx.coroutines.future.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A dispatcher with one virtual thread per resume.
 *
 * Every fork and every build entry point runs its coroutines here, so no CPU work of the build reaches the kotlinx scheduler.
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

  /** Every fork runs to its end. Then the first failure is thrown with the others suppressed. */
  RUN_ALL,
}

/**
 * Starts [block] as a root coroutine on [buildVirtualThreadDispatcher], and returns the future of its result.
 *
 * The body sees the OpenTelemetry context of the caller and a [CoroutineName] of [name]. A cancelled future cancels
 * the body. An awaiter that must not cancel the body on its own cancellation uses [awaitShared].
 */
@ApiStatus.Internal
fun <T> forkOnVirtualThread(
  name: String,
  context: CoroutineContext = EmptyCoroutineContext,
  block: suspend CoroutineScope.() -> T,
): CompletableFuture<T> {
  @Suppress("RAW_SCOPE_CREATION") // a fork is a root, like the `runBlocking` entry it replaces; the future owns its lifetime
  val scope = CoroutineScope(buildVirtualThreadDispatcher + CoroutineName(name) + Context.current().asContextElement() + context)
  return scope.future { block() }
}

/**
 * Waits for a future that other awaiters share. A cancelled awaiter cancels only its own copy, and the shared
 * computation goes on.
 *
 * The plain `await` cancels the future when the awaiter is cancelled. That is right for a one-shot future only.
 */
@ApiStatus.Internal
suspend fun <T> CompletableFuture<T>.awaitShared(): T = copy().await()

/** The exception of a future that completed exceptionally, without the `CompletionException` wrapper of `join`. */
@ApiStatus.Internal
fun CompletableFuture<*>.failureOrNull(): Throwable? {
  if (!isCompletedExceptionally) {
    return null
  }
  val e = runCatching { join() }.exceptionOrNull() ?: return null
  return (e as? CompletionException)?.cause ?: e
}

/**
 * The entry of a build script: runs [block] on virtual threads and blocks the calling thread until it ends.
 *
 * It replaces `runBlocking(Dispatchers.Default)`. The calling thread only waits; every resume of [block] and of its
 * children runs on [buildVirtualThreadDispatcher], so the kotlinx scheduler gets no CPU work of the build.
 */
@ApiStatus.Internal
@Suppress("SSBasedInspection") // a build script has no progress indicator, so `runBlocking` is the right entry
fun <T> runBlockingOnVirtualThreads(block: suspend CoroutineScope.() -> T): T {
  return runBlocking(buildVirtualThreadDispatcher) { block() }
}

/**
 * A group of forks that [virtualThreadTasks] joins when its block ends.
 *
 * It replaces `coroutineScope` with `async` and `launch` children. A fork is a root coroutine on virtual threads,
 * and the group holds no queue, so a scheduler cannot lose a fork.
 */
@ApiStatus.Internal
class VirtualThreadTasks internal constructor(private val policy: VirtualThreadTaskPolicy) {
  private val futures = CopyOnWriteArrayList<CompletableFuture<*>>()

  /**
   * The failure that ended the first fork. A [CancellationException] that a fork throws itself counts, because
   * `CompletableFuture.isCancelled` cannot tell it from a cancellation by [cancelAll].
   */
  private val firstFailure = AtomicReference<Throwable?>()

  @Volatile
  private var cancelRequested = false

  @Volatile
  private var closed = false

  /** Starts [block] on virtual threads. See [forkOnVirtualThread]. */
  fun <T> fork(name: String, block: suspend CoroutineScope.() -> T): CompletableFuture<T> {
    check(!closed) { "The group of forks has ended, so it cannot start '$name'" }
    val future = forkOnVirtualThread(name = name, block = block)
    futures.add(future)
    future.whenComplete { _, e ->
      if (e != null && !cancelRequested) {
        firstFailure.compareAndSet(null, e)
        if (policy == VirtualThreadTaskPolicy.FAIL_FAST) {
          cancelAll()
        }
      }
    }
    return future
  }

  internal fun cancelAll() {
    cancelRequested = true
    for (future in futures) {
      future.cancel(true)
    }
  }

  /**
   * Waits for every fork, including a fork that another fork adds meanwhile, and closes the group.
   *
   * Throws the first failure with the other failures suppressed. A fork that the group cancelled is not a failure.
   * When the caller is cancelled, every fork is cancelled.
   */
  internal suspend fun joinAll() {
    try {
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
    }
    finally {
      closed = true
    }

    val first = firstFailure.get() ?: return
    for (future in futures) {
      val e = future.failureOrNull() ?: continue
      if (e !== first && e !is CancellationException) {
        first.addSuppressed(e)
      }
    }
    throw first
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
