package com.intellij.platform.buildScripts.concurrency

import io.opentelemetry.context.Context
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration

/** Owns shared workers independently of the callers that wait for their results. */
@ApiStatus.Internal
class SharedTaskOwner(val name: String) : AutoCloseable {
  private val lock = Any()
  private val workers = LinkedHashSet<SharedComputation<*>>()
  private val completedWorkers = ArrayDeque<SharedComputation<*>>()
  private val resources = ArrayDeque<AutoCloseable>()
  private val cancellation = TaskCancellation()
  private val closed = CompletableFuture<Unit>()

  @Volatile
  private var closing: Thread? = null

  fun checkOpen() {
    check(closing == null && !closed.isDone) { "Shared task owner '$name' is closed" }
  }

  fun onClose(resource: AutoCloseable) {
    val closeNow = synchronized(lock) {
      if (closing != null || closed.isDone) true
      else {
        resources.addFirst(resource)
        false
      }
    }
    if (closeNow) resource.close()
  }

  internal fun <T> start(name: String, singleFlightOwner: Any, loader: () -> T): SharedComputation<T> {
    val telemetry = Context.current()
    val inherited = currentSingleFlightOwners() + this
    val result = CompletableFuture<T>()
    val workerCancellation = TaskCancellation()
    val context = TaskContext(workerCancellation, name)
    lateinit var parentLink: AutoCloseable
    lateinit var computation: SharedComputation<T>
    val thread = Thread.ofVirtual().name(name).unstarted(ForgetfulTask {
      try {
        telemetry.makeCurrent().use {
          withSingleFlightOwners(inherited, singleFlightOwner) { context.run { result.complete(loader()) } }
        }
      }
      catch (failure: Throwable) {
        result.completeExceptionally(failure)
      }
      finally {
        parentLink.close()
        synchronized(lock) {
          completedWorkers.addLast(computation)
        }
      }
    })
    computation = SharedComputation(thread, result, workerCancellation)
    synchronized(lock) {
      checkOpen()
      parentLink = cancellation.onCancel { computation.cancel() }
      while (completedWorkers.firstOrNull()?.thread?.state == Thread.State.TERMINATED) {
        workers.remove(completedWorkers.removeFirst())
      }
      workers.add(computation)
      try {
        thread.start()
      }
      catch (failure: Throwable) {
        workers.remove(computation)
        parentLink.close()
        throw failure
      }
    }
    return computation
  }

  override fun close() {
    check(this !in currentSingleFlightOwners()) { "A shared worker cannot close its own owner '$name'" }
    check(closing !== Thread.currentThread()) { "Shared task owner '$name' cannot close recursively" }
    val owned = synchronized(lock) {
      if (closing != null || closed.isDone) null
      else {
        closing = Thread.currentThread()
        workers.toList() to resources.toList()
      }
    }
    if (owned == null) {
      closed.awaitUninterruptibly()
      return
    }
    var interrupted = Thread.interrupted()
    var failure: Throwable? = null
    try {
      cancellation.cancel(ScopeCancelledException())
      for (computation in owned.first) computation.cancel()
      for (computation in owned.first) {
        interrupted = joinUninterruptibly(computation.thread) || interrupted
      }
      for (resource in owned.second) {
        try {
          resource.close()
        }
        catch (error: Throwable) {
          failure = accumulateFailure(failure, error)
        }
      }
    }
    finally {
      synchronized(lock) {
        workers.clear()
        completedWorkers.clear()
        resources.clear()
        if (failure == null) closed.complete(Unit) else closed.completeExceptionally(failure)
        closing = null
      }
      if (interrupted) Thread.currentThread().interrupt()
    }
    failure?.let { throw it }
  }

}

internal class SharedComputation<T>(
  val thread: Thread,
  val result: CompletableFuture<T>,
  private val cancellation: TaskCancellation,
) : Awaitable<T> {
  override fun await(timeout: Duration?): T = result.awaitResult(timeout)

  fun cancel() {
    cancellation.cancel(ScopeCancelledException())
    thread.interrupt()
  }
}

internal fun joinUninterruptibly(thread: Thread): Boolean {
  var interrupted = false
  while (thread.isAlive) {
    try {
      thread.join()
    }
    catch (_: InterruptedException) {
      interrupted = true
    }
  }
  return interrupted
}

internal fun accumulateFailure(previous: Throwable?, failure: Throwable): Throwable {
  if (previous == null) return failure
  if (previous !== failure) previous.addSuppressed(failure)
  return previous
}
