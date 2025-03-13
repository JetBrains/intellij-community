// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.events

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.impl.DebuggerTaskImpl
import com.intellij.debugger.impl.PrioritizedTask
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.CoroutineContext.Key

abstract class DebuggerCommandImpl(override val priority: PrioritizedTask.Priority = PrioritizedTask.Priority.LOW)
  : DebuggerTaskImpl(), CoroutineContext.Element {

  /**
   * [Job] tied to this command execution.
   *
   * The job is saved to have a possibility to cancel all the work started within the command when
   * [notifyCancelled] is called.
   * For example, a command can start a computation in [kotlinx.coroutines.Dispatchers.Default].
   * When the program is resumed, the job will be canceled.
   */
  @Volatile
  private var commandJob: Job? = null
    set(value) {
      val old = field
      if (value != null && old != null && !old.isCompleted) error("Command job is already set")
      field = value
    }

  private val continuation = AtomicReference<Runnable>(null)
  private var myThread: DebuggerManagerThreadImpl? = null
  protected val commandManagerThread: DebuggerManagerThreadImpl
    get() = myThread ?: error("DebuggerManagerThread is not set")

  internal fun setCommandManagerThread(value: DebuggerManagerThreadImpl) {
    if (myThread != null && myThread !== value) {
      error("DebuggerManagerThread is already set")
    }
    myThread = value
  }

  @Throws(Exception::class)
  protected open fun action(): Unit = throw AbstractMethodError()

  @ApiStatus.Experimental
  protected open suspend fun actionSuspend(): Unit = action()
  protected open fun commandCancelled() {
  }

  @ApiStatus.Internal
  fun notifyCancelled() {
    try {
      commandCancelled()
    }
    finally {
      myThread?.unfinishedCommands?.remove(this)
      release()
      cancelCommandScope()
      // The continuation must be called by the CoroutineDispatcher contract.
      // It should do nothing, as the scope is canceled.
      executeContinuation()
    }
  }

  @Throws(Exception::class)
  private suspend fun runSuspend() {
    val commands = commandManagerThread.unfinishedCommands
    commands.add(this)
    try {
      actionSuspend()
      check(resetContinuation(null) == null) { "Continuation is not null after command is completed" }
    }
    finally {
      commands.remove(this)
      release()
    }
  }

  @ApiStatus.Internal
  protected open fun onSuspendOrFinish() {
  }

  @ApiStatus.Internal
  protected open fun invokeContinuation(): Unit = executeContinuation()

  @ApiStatus.Internal
  fun cancelCommandScope() {
    commandJob?.cancel()
  }

  internal fun resetContinuation(runnable: Runnable?): Runnable? = continuation.getAndSet(runnable)

  @ApiStatus.Internal
  protected fun executeContinuation() {
    resetContinuation(null)?.run()
  }

  internal fun invokeCommand(dispatcher: CoroutineDispatcher, parentScope: CoroutineScope) {
    val exception = AtomicReference<Throwable>()
    if (continuation.get() == null) {
      // executed synchronously until the first suspend, resume is handled by dispatcher
      val job = parentScope.async(this + dispatcher, start = CoroutineStart.UNDISPATCHED) {
        runSuspend()
      }.also {
        commandJob = it
      }
      onSuspendOrFinish()
      handleCompletionException(job, exception)
    }
    else {
      invokeContinuation()
      onSuspendOrFinish()
    }
    exception.getAndSet(null)?.also { throw it }
  }

  object KEY : Key<DebuggerCommandImpl>

  override val key: Key<DebuggerCommandImpl> = KEY

  //*** Do not remove
  override fun <R> fold(initial: R, operation: (R, CoroutineContext.Element) -> R): R {
    return super.fold(initial, operation)
  }

  //*** Do not remove
  override fun <E : CoroutineContext.Element> get(key: Key<E>): E? {
    return super.get(key)
  }

  //*** Do not remove
  override fun minusKey(key: Key<*>): CoroutineContext {
    return super.minusKey(key)
  }

  //*** Do not remove
  override fun plus(context: CoroutineContext): CoroutineContext {
    return super.plus(context)
  }
}

/**
 * Sets completion exception into [exceptionReference].
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun handleCompletionException(job: Deferred<Unit>, exceptionReference: AtomicReference<Throwable>) {
  if (job.isCompleted) {
    val completionException = job.getCompletionExceptionOrNull() ?: return
    if (completionException !is CancellationException) {
      exceptionReference.set(completionException)
    }
  }
  else {
    job.invokeOnCompletion {
      val completionException = it ?: return@invokeOnCompletion
      if (completionException !is CancellationException) {
        exceptionReference.set(completionException)
      }
    }
  }
}
