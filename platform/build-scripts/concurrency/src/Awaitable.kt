package com.intellij.platform.buildScripts.concurrency

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/** A result that can be awaited without transferring ownership to the waiter. */
@ApiStatus.Internal
interface Awaitable<out T> {
  /** Stops only this wait when the caller is interrupted or [timeout] expires. */
  fun await(timeout: Duration? = null): T

  companion object {
    fun <T> completed(value: T): Awaitable<T> = object : Awaitable<T> {
      override fun await(timeout: Duration?): T {
        checkInterrupted()
        return value
      }
    }
  }
}

/** An interruptible result gate. Completing it does not start or cancel work. */
@ApiStatus.Internal
class TaskSignal<T>(private val name: String = "task signal") : Awaitable<T> {
  private val future = CompletableFuture<T>()

  val isDone: Boolean get() = future.isDone

  fun complete(value: T): Boolean = future.complete(value)

  fun fail(failure: Throwable): Boolean = future.completeExceptionally(failure)

  fun onCompletion(action: () -> Unit) {
    future.whenComplete { _, _ -> action() }
  }

  override fun await(timeout: Duration?): T = awaitTask(name) { future.awaitResult(timeout) }
}

internal fun <T> awaitTask(name: String, block: () -> T): T {
  val context = TaskContext.current.get() ?: return block()
  val previous = context.waitingFor
  context.waitingFor = name
  try {
    return block()
  }
  finally {
    context.waitingFor = previous
  }
}

internal fun checkInterrupted() {
  if (Thread.interrupted()) {
    throw InterruptedException()
  }
}

internal fun <T> CompletableFuture<T>.awaitResult(timeout: Duration? = null): T {
  checkInterrupted()
  try {
    return if (timeout == null || timeout == Duration.INFINITE) get()
    else get(timeout.inWholeNanoseconds.coerceAtLeast(0), TimeUnit.NANOSECONDS)
  }
  catch (failure: ExecutionException) {
    throw failure.cause ?: failure
  }
  catch (failure: CancellationException) {
    throw failureOrNull() ?: failure
  }
}

/** Waits for completion despite caller interruptions, then restores the interrupt status. */
@ApiStatus.Internal
fun <T> CompletableFuture<T>.awaitUninterruptibly(): T {
  var interrupted = Thread.interrupted()
  try {
    while (true) {
      try {
        return get()
      }
      catch (_: InterruptedException) {
        interrupted = true
      }
      catch (failure: ExecutionException) {
        throw failure.cause ?: failure
      }
    }
  }
  finally {
    if (interrupted) Thread.currentThread().interrupt()
  }
}

/** Returns the original failure without changing the future. */
internal fun CompletableFuture<*>.failureOrNull(): Throwable? {
  if (!isCompletedExceptionally) return null
  return handle { _, failure -> failure }.getNow(null)
}
