// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A dispatcher with one virtual thread per resume.
 *
 * Every fork and every build entry point runs its coroutines here, so no CPU work of the build reaches the kotlinx scheduler.
 */
internal val buildVirtualThreadDispatcher: CoroutineDispatcher = virtualThreadPerResumeDispatcher("build-")

/**
 * Creates a dispatcher with one virtual thread per resume. [threadNamePrefix] names the threads, with a counter.
 *
 * A finished thread keeps no task, see [ForgetfulTask]. `close` releases nothing, because no thread outlives its resume.
 */
@ApiStatus.Internal
fun virtualThreadPerResumeDispatcher(threadNamePrefix: String): ExecutorCoroutineDispatcher = VirtualThreadPerResumeDispatcher(threadNamePrefix)

private class VirtualThreadPerResumeDispatcher(threadNamePrefix: String) : ExecutorCoroutineDispatcher() {
  private val threadFactory = Thread.ofVirtual().name(threadNamePrefix, 0).factory()

  override val executor: Executor = Executor { task -> threadFactory.newThread(ForgetfulTask(task)).start() }

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    executor.execute(block)
  }

  override fun close() {
  }
}

/**
 * The body of a virtual thread. It drops its task before it runs the task.
 *
 * A dead virtual thread keeps its task through its continuation. A span, a debug probe or a logger can hold the
 * thread. Such a reference would keep the whole coroutine alive.
 */
private class ForgetfulTask(task: Runnable) : Runnable {
  private var task: Runnable? = task

  override fun run() {
    val task = task ?: return
    this.task = null
    task.run()
  }
}

/**
 * What a group of forks does when one of them fails.
 */
@ApiStatus.Internal
enum class TaskScopePolicy {
  /** The first failure cancels the other forks. This is the behaviour of `coroutineScope`. */
  FAIL_FAST,

  /** Every fork runs to its end. Then the first failure is thrown with the others suppressed. */
  RUN_ALL,
}

/**
 * Starts [block] as a root coroutine on [buildVirtualThreadDispatcher], and returns the future of its result.
 *
 * This is the primitive of a shared computation, such as an entry of `AsyncCache`, that outlives its first caller.
 * A fan-out inside the pipeline uses [taskScope] instead.
 *
 * The body inherits the elements of [parentContext] except its job and its dispatcher, so it sees the telemetry span
 * and the single-flight element of the caller, under a [CoroutineName] of [name]. A cancelled future cancels the
 * body. An awaiter that must not cancel the body on its own cancellation uses [awaitShared].
 */
@ApiStatus.Internal
fun <T> forkOnVirtualThread(
  name: String,
  parentContext: CoroutineContext,
  block: suspend CoroutineScope.() -> T,
): CompletableFuture<T> {
  return startFork(name = name, parentContext = parentContext, block = block).asCompletableFuture()
}

internal fun <T> startFork(name: String, parentContext: CoroutineContext, block: suspend CoroutineScope.() -> T): Deferred<T> {
  val inherited = parentContext.minusKey(Job).minusKey(ContinuationInterceptor)
  @Suppress("RAW_SCOPE_CREATION") // a fork is a root, like the `runBlocking` entry it replaces; its group or its future owns its lifetime
  val scope = CoroutineScope(inherited + buildVirtualThreadDispatcher + CoroutineName(name))
  return scope.async { block() }
}

/**
 * Starts [block] on a virtual thread of its own, and returns the future of its result.
 *
 * This is the primitive of a shared computation, such as an entry of `AsyncCache`, that outlives its first caller.
 * The computation is not a child of that caller: once it starts it runs to its end, and every caller only waits with
 * [joinShared]. A fan-out inside the pipeline uses [taskScope] instead.
 *
 * The body runs inside the single-flight computations of the caller, so a recursive wait for the same key fails fast.
 * It carries no telemetry context. A caller whose computation opens a span passes the telemetry context into the
 * entry it runs, see [runBlockingOnVirtualThreads].
 */
@ApiStatus.Internal
fun <T> startSharedComputation(name: String, owner: Any?, block: () -> T): CompletableFuture<T> {
  val future = CompletableFuture<T>()
  val inherited = currentSingleFlightOwners()
  val body = Runnable {
    try {
      future.complete(withSingleFlightOwners(inherited = inherited, owner = owner, body = block))
    }
    catch (e: Throwable) {
      future.completeExceptionally(e)
    }
  }
  Thread.ofVirtual().name(name).start(ForgetfulTask(body))
  return future
}

/**
 * Waits for a future that other waiters share, without blocking the thread.
 *
 * For a caller that is still a coroutine and must stay cancellable. A cancelled waiter cancels only its own copy, so
 * the shared computation goes on. The blocking twin is [joinShared].
 */
@ApiStatus.Internal
suspend fun <T> CompletableFuture<T>.awaitShared(): T = copy().await()

/**
 * Waits for a future that other waiters share, and throws its failure as the computation threw it.
 *
 * `join` needs no defensive copy, because it cancels nothing: a waiter that goes away changes nothing for the
 * computation or for the other waiters. `join` also ignores an interrupt and re-asserts the flag before it returns,
 * which is the rule of the pipeline already. Only a group cancels a fork, and a blocking body does not see that
 * cancel. Do not replace it with `get`, whose `InterruptedException` a fork group would count as a failure.
 */
@ApiStatus.Internal
fun <T> CompletableFuture<T>.joinShared(): T {
  try {
    return join()
  }
  catch (e: CompletionException) {
    // `await` throws the cause, and every caller of this cache expects the same
    throw e.cause ?: e
  }
}

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
 * [context] adds elements to the coroutine, such as the telemetry context of the caller that starts a shared computation.
 * The dispatcher and the single-flight guard of this entry win over the same elements of [context].
 */
@ApiStatus.Internal
@Suppress("SSBasedInspection") // a build script has no progress indicator, so `runBlocking` is the right entry
fun <T> runBlockingOnVirtualThreads(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> T): T {
  // the only blocking-to-coroutine boundary of a build, so the single-flight guard is seeded here and nowhere else
  return runBlocking(context + buildVirtualThreadDispatcher + singleFlightContextElement()) { block() }
}

/**
 * The handle of a fork, as `StructuredTaskScope.Subtask` is in the JDK.
 *
 * It only hands out the result. Only the group cancels a fork: an awaiter that is cancelled stops waiting and
 * changes nothing for the fork or for the other awaiters.
 */
@ApiStatus.Internal
class Subtask<T> internal constructor(private val deferred: Deferred<T>) {
  /** Returns the result of the fork, or throws its failure. A fork that the group cancelled throws [CancellationException]. */
  suspend fun await(): T = deferred.await()
}

/**
 * A group of forks that [taskScope] joins when its block ends.
 *
 * It replaces `coroutineScope` with `async` and `launch` children. A fork is a root coroutine on virtual threads,
 * and the group holds no queue, so a scheduler cannot lose a fork.
 */
@ApiStatus.Internal
class TaskScope internal constructor(
  private val policy: TaskScopePolicy,
  /** The context of the coroutine that opened the group. Every fork inherits it, see [forkOnVirtualThread]. */
  private val parentContext: CoroutineContext,
) {
  private val forks = CopyOnWriteArrayList<Deferred<*>>()

  /**
   * The failures of the forks in the order they ended. A [CancellationException] counts only when the group did not
   * request it, because a fork that throws one itself is a failure of the fork.
   */
  private val failures = CopyOnWriteArrayList<Throwable>()

  @Volatile
  private var cancelRequested = false

  @Volatile
  private var closed = false

  /** Starts [block] on virtual threads. See [forkOnVirtualThread]. */
  fun <T> fork(name: String, block: suspend CoroutineScope.() -> T): Subtask<T> {
    check(!closed) { "The group of forks has ended, so it cannot start '$name'" }
    val deferred = startFork(name = name, parentContext = parentContext, block = block)
    forks.add(deferred)
    // the handler runs at once when the fork has ended already, and it must not throw
    deferred.invokeOnCompletion { e ->
      if (e != null && !isRequestedCancellation(e)) {
        failures.add(e)
        if (policy == TaskScopePolicy.FAIL_FAST) {
          cancelAll()
        }
      }
    }
    if (cancelRequested) {
      // a fork that a cancelled fork starts must not outlive the cancel
      deferred.cancel(TaskScopeCancellationException(this))
    }
    return Subtask(deferred)
  }

  internal fun cancelAll() {
    cancelRequested = true
    for (fork in forks) {
      fork.cancel(TaskScopeCancellationException(this))
    }
  }

  private fun isRequestedCancellation(e: Throwable): Boolean {
    return e is TaskScopeCancellationException && e.taskScope === this
  }

  /**
   * Waits for every fork to end, including a fork that another fork adds meanwhile, and closes the group.
   *
   * A cancelled fork has ended only when its body has returned, so no fork works on after the group. Throws the
   * first failure with the other failures suppressed. A fork that the group cancelled is not a failure. When the
   * caller is cancelled, every fork is cancelled and waited for.
   */
  internal suspend fun joinAll() {
    try {
      try {
        joinPending()
      }
      catch (e: CancellationException) {
        cancelAll()
        withContext(NonCancellable) {
          joinPending()
        }
        throw e
      }
    }
    finally {
      closed = true
    }

    val first = failures.firstOrNull() ?: return
    for (e in failures) {
      if (e !== first && e !is CancellationException) {
        first.addSuppressed(e)
      }
    }
    throw first
  }

  /** `join` of a deferred returns when its body has ended, and throws only when the caller is cancelled. */
  private suspend fun joinPending() {
    while (true) {
      val pending = forks.filter { !it.isCompleted }
      if (pending.isEmpty()) {
        return
      }
      for (fork in pending) {
        fork.join()
      }
    }
  }
}

private class TaskScopeCancellationException(
  @JvmField val taskScope: TaskScope,
) : CancellationException("The group of forks cancelled the fork")

/**
 * Runs [block] with a group of forks, and waits for every fork before it returns.
 *
 * A failure of [block] cancels the forks and is rethrown with the failures of the forks suppressed. A failure of
 * a fork follows [policy]. A fork failure is never hidden behind a cancellation: when a failed fork makes the group
 * cancel a fork that [block] awaits, the failure is thrown and not the [CancellationException] that the await saw.
 * This is the replacement for `coroutineScope` and `supervisorScope` in the packaging pipeline.
 */
@ApiStatus.Internal
suspend fun <T> taskScope(
  policy: TaskScopePolicy = TaskScopePolicy.FAIL_FAST,
  block: suspend TaskScope.() -> T,
): T {
  val tasks = TaskScope(policy = policy, parentContext = currentCoroutineContext())
  val result = try {
    tasks.block()
  }
  catch (e: Throwable) {
    tasks.cancelAll()
    val forkFailure = runCatching { tasks.joinAll() }.exceptionOrNull()
    when {
      // a cancellation is never suppressed: the caller's own cancellation must stay a plain one
      forkFailure == null || forkFailure is CancellationException -> throw e
      // the block saw only the cancellation that the failed fork caused
      e is CancellationException -> throw forkFailure
      else -> {
        e.addSuppressed(forkFailure)
        throw e
      }
    }
  }
  tasks.joinAll()
  return result
}
