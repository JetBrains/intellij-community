package com.intellij.platform.buildScripts.concurrency

import io.opentelemetry.context.Context
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration

/** Defines how a scope reports completion. A joiner does not own worker threads. */
@ApiStatus.Internal
class Joiner private constructor(internal val failFast: Boolean, internal val reportFailures: Boolean) {
  companion object {
    fun awaitAllSuccessfulOrThrow(): Joiner = Joiner(failFast = true, reportFailures = true)
    fun awaitAll(): Joiner = Joiner(failFast = false, reportFailures = false)
    fun awaitAllOrThrow(): Joiner = Joiner(failFast = false, reportFailures = true)
  }
}

/** Separates a worker failure from interruption of the caller. */
@ApiStatus.Internal
class TaskFailedException(cause: Throwable) : RuntimeException("A build task failed: ${cause.message}", cause)

internal class ScopeAccess(val ownerId: Long, @Volatile var joined: Boolean = false)

/** A scope-owned result. Waiting for it does not transfer cancellation ownership. */
@ApiStatus.Internal
class Subtask<T> internal constructor(val name: String, private val access: ScopeAccess) : Awaitable<T> {
  enum class State { UNAVAILABLE, SUCCESS, FAILED }

  private val result = CompletableFuture<T>()

  @Volatile
  private var outcome = State.UNAVAILABLE
  private var failure: Throwable? = null
  private var value: T? = null

  fun state(): State = outcome

  fun onCompletion(action: (Throwable?) -> Unit) {
    result.whenComplete { _, failure -> action(failure) }
  }

  fun get(): T {
    checkAccess()
    check(outcome == State.SUCCESS) { "Task '$name' has no successful result" }
    @Suppress("UNCHECKED_CAST")
    return value as T
  }

  fun exception(): Throwable {
    checkAccess()
    check(outcome == State.FAILED) { "Task '$name' has no failure" }
    return checkNotNull(failure)
  }

  override fun await(timeout: Duration?): T {
    check(TaskContext.current.get()?.task !== this) { "Task '$name' cannot await itself" }
    return awaitTask(name) { result.awaitResult(timeout) }
  }

  private fun checkAccess() {
    check(access.ownerId != Thread.currentThread().threadId() || access.joined) { "The owner must join before reading '$name'" }
  }

  internal fun complete(value: T) {
    this.value = value
    outcome = State.SUCCESS
  }

  internal fun fail(error: Throwable) {
    failure = error
    outcome = State.FAILED
  }

  internal fun publish() {
    when (outcome) {
      State.SUCCESS -> {
        @Suppress("UNCHECKED_CAST")
        result.complete(value as T)
      }
      State.FAILED -> result.completeExceptionally(TaskFailedException(checkNotNull(failure)))
      State.UNAVAILABLE -> Unit
    }
  }

  internal fun cancel(error: Throwable) {
    result.completeExceptionally(error)
  }
}

/** Owns worker threads. Only the creating thread can fork, join, or close this scope. */
@TaskScopeDsl
@ApiStatus.Internal
class TaskScope private constructor(
  val name: String,
  private val joiner: Joiner,
  timeout: Duration?,
  private val threadFactory: ThreadFactory,
) : AutoCloseable {
  private val owner = Thread.currentThread()
  private val access = ScopeAccess(owner.threadId())
  private val lock = ReentrantLock()
  private val changed = lock.newCondition()
  private val workers = ArrayList<Pair<Thread, Subtask<*>>>()
  private val failures = ArrayList<Throwable>()
  private val cancellation = TaskCancellation()
  private val previousScope = current.get()
  private var pending = 0
  private var joinAttempted = false
  private var abandoned = false
  private var closed = false
  private val parentLink: AutoCloseable?
  private val deadline: java.util.concurrent.ScheduledFuture<*>?

  val isCancelled: Boolean get() = cancellation.cause != null

  init {
    require(timeout == null || timeout.isFinite() && timeout > Duration.ZERO) { "The scope timeout must be positive and finite" }
    parentLink = (previousScope?.cancellation ?: TaskContext.current.get()?.cancellation)?.onCancel(::cancel)
    deadline = if (timeout == null) null
    else Timer.executor.schedule(
      { cancel(TimeoutException("Task scope '$name' exceeded $timeout")) }, timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS,
    )
    current.set(this)
  }

  fun checkCancelled() {
    checkInterrupted()
    cancellation.cause?.let { throw it }
  }

  fun <T> fork(name: String, block: TaskContext.() -> T): Subtask<T> {
    checkOwner()
    try {
      return lock.withLock {
        check(!closed && !joinAttempted) { "Cannot fork '$name' after joining or closing '$this'" }
        val subtask = Subtask<T>(name, access)
        val cancelled = cancellation.cause
        if (cancelled != null) {
          subtask.cancel(cancelled)
          return@withLock subtask
        }
        val telemetry = Context.current()
        val owners = currentSingleFlightOwners()
        val context = TaskContext(cancellation, name, subtask)
        val thread = threadFactory.newThread(ForgetfulTask {
          try {
            val value = telemetry.makeCurrent().use {
              withSingleFlightOwners(owners, null) { context.run(block) }
            }
            record(subtask, value, null)
          }
          catch (failure: Throwable) {
            record(subtask, null, failure)
          }
        }) ?: error("The thread factory did not create '$name'")
        check(thread.state == Thread.State.NEW) { "The thread factory returned a started thread" }
        thread.name = name
        workers.add(thread to subtask)
        pending++
        try {
          thread.start()
        }
        catch (failure: Throwable) {
          workers.removeAt(workers.lastIndex)
          pending--
          throw failure
        }
        subtask
      }
    }
    catch (failure: Throwable) {
      cancel(failure)
      throw failure
    }
  }

  private fun <T> record(subtask: Subtask<T>, value: T?, failure: Throwable?) {
    lock.withLock {
      pending--
      if (cancellation.cause == null) {
        if (failure == null) {
          @Suppress("UNCHECKED_CAST")
          subtask.complete(value as T)
        }
        else {
          failures.add(failure)
          subtask.fail(failure)
        }
      }
      changed.signalAll()
    }
    if (failure != null && joiner.failFast) cancel(TaskFailedException(failure))
    subtask.publish()
  }

  fun join() {
    checkOwner()
    check(!joinAttempted && !closed) { "Task scope '$name' permits one join" }
    joinAttempted = true
    try {
      checkInterrupted()
      lock.lockInterruptibly()
      try {
        while (pending != 0 && cancellation.cause == null) changed.await()
        access.joined = true
        cancellation.cause?.let { throw it }
        if (joiner.reportFailures && failures.isNotEmpty()) {
          val failure = TaskFailedException(failures.first())
          failures.drop(1).filter { it !== failure.cause }.forEach(failure::addSuppressed)
          throw failure
        }
      }
      finally {
        lock.unlock()
      }
    }
    catch (failure: InterruptedException) {
      cancel(failure)
      Thread.currentThread().interrupt()
      throw failure
    }
  }

  fun <T> join(result: () -> T): T {
    join()
    return result()
  }

  /** Marks the owner as unwinding a failure, so [close] does not report a missing join. */
  fun abandon() {
    checkOwner()
    abandoned = true
  }

  private fun cancel(failure: Throwable) {
    if (!cancellation.cancel(failure)) return
    val cancelledWorkers = lock.withLock {
      changed.signalAll()
      workers.toList()
    }
    for ((thread, subtask) in cancelledWorkers) {
      if (thread !== Thread.currentThread()) thread.interrupt()
      if (subtask.state() == Subtask.State.UNAVAILABLE) subtask.cancel(failure)
    }
  }

  override fun close() {
    checkOwner()
    if (closed) return
    check(current.get() === this) { "Nested task scopes must close before their parent" }
    val missingJoin = !joinAttempted && !abandoned && workers.isNotEmpty()
    cancel(ScopeCancelledException())
    deadline?.cancel(false)
    var interrupted = Thread.interrupted()
    try {
      for ((thread, _) in workers) {
        while (thread.isAlive) {
          try {
            thread.join()
          }
          catch (_: InterruptedException) {
            interrupted = true
          }
        }
      }
    }
    finally {
      parentLink?.close()
      lock.withLock {
        closed = true
        workers.clear()
        failures.clear()
      }
      if (previousScope == null) current.remove() else current.set(previousScope)
      if (interrupted) Thread.currentThread().interrupt()
    }
    check(!missingJoin) { "Task scope '$name' closed without a join" }
  }

  private fun checkOwner() {
    check(Thread.currentThread() === owner) { "Only the owner can manage task scope '$name'" }
  }

  companion object {
    internal val current = ThreadLocal<TaskScope>()

    fun open(
      name: String = Thread.currentThread().name.ifEmpty { "build tasks" },
      joiner: Joiner = Joiner.awaitAllSuccessfulOrThrow(),
      timeout: Duration? = null,
      threadFactory: ThreadFactory = Thread.ofVirtual().factory(),
    ): TaskScope = TaskScope(name, joiner, timeout, threadFactory)
  }

  private object Timer {
    val executor = ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("build scope deadlines").factory()).apply {
      removeOnCancelPolicy = true
    }
  }
}

/** Runs on the caller and closes the scope. The body must explicitly call [TaskScope.join]. */
@ApiStatus.Internal
inline fun <T> taskScope(
  name: String = Thread.currentThread().name.ifEmpty { "build tasks" },
  joiner: Joiner = Joiner.awaitAllSuccessfulOrThrow(),
  timeout: Duration? = null,
  block: TaskScope.() -> T,
): T = TaskScope.open(name, joiner, timeout).use { scope ->
  try {
    scope.block()
  }
  catch (failure: Throwable) {
    scope.abandon()
    throw failure
  }
}
