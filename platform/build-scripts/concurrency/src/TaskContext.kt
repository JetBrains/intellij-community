package com.intellij.platform.buildScripts.concurrency

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException

@DslMarker
@ApiStatus.Internal
annotation class TaskScopeDsl

/** The cancellation context of a worker. Scope management belongs to its owner. */
@TaskScopeDsl
@ApiStatus.Internal
class TaskContext internal constructor(internal val cancellation: TaskCancellation, val name: String, internal val task: Any? = null) {
  @Volatile
  var waitingFor: String? = null
    internal set

  val isCancelled: Boolean get() = cancellation.cause != null || Thread.currentThread().isInterrupted

  fun checkCancelled() {
    checkInterrupted()
    cancellation.cause?.let { throw it }
  }

  internal fun <T> run(block: TaskContext.() -> T): T {
    val previous = current.get()
    current.set(this)
    try {
      checkCancelled()
      return block()
    }
    finally {
      if (previous == null) current.remove() else current.set(previous)
    }
  }

  internal companion object {
    val current = ThreadLocal<TaskContext>()
  }
}

internal class TaskCancellation {
  private val listeners = LinkedHashSet<(Throwable) -> Unit>()

  @Volatile
  var cause: Throwable? = null
    private set

  fun cancel(failure: Throwable): Boolean {
    val actions = synchronized(listeners) {
      if (cause != null) return false
      cause = failure
      listeners.toList().also { listeners.clear() }
    }
    for (action in actions) {
      try {
        action(failure)
      }
      catch (error: Throwable) {
        if (error !== failure) failure.addSuppressed(error)
      }
    }
    return true
  }

  fun onCancel(action: (Throwable) -> Unit): AutoCloseable {
    val failure = synchronized(listeners) {
      cause.also { if (it == null) listeners.add(action) }
    }
    if (failure != null) action(failure)
    return AutoCloseable { synchronized(listeners) { listeners.remove(action) } }
  }
}

internal class ScopeCancelledException : CancellationException("The task owner cancelled the work")

/** Drops the captured action before a virtual thread runs it. */
internal class ForgetfulTask(private var action: Runnable?) : Runnable {
  override fun run() {
    val work = action ?: return
    action = null
    work.run()
  }
}
