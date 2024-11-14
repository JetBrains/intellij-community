// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.events

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.DebuggerThreadDispatcher
import com.intellij.debugger.impl.DebuggerTaskImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.CoroutineContext.Key

abstract class DebuggerCommandImpl(private val myPriority: PrioritizedTask.Priority = PrioritizedTask.Priority.LOW)
  : DebuggerTaskImpl(), CoroutineContext.Element {
  @Volatile
  protected var myCoroutineScope: CoroutineScope? = null
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
  protected open fun action() {
    throw AbstractMethodError()
  }

  @ApiStatus.Experimental
  protected open suspend fun actionSuspend() {
    action()
  }

  protected open fun commandCancelled() {
  }

  override fun getPriority() = myPriority

  fun notifyCancelled() {
    try {
      commandCancelled()
    }
    finally {
      myThread?.unfinishedCommands?.remove(this)
      release()
      cancelCommandScope()
      executeContinuation()
    }
  }

  @Throws(Exception::class)
  private suspend fun runSuspend() {
    val commands = commandManagerThread.unfinishedCommands
    commands.add(this)
    try {
      actionSuspend()
    }
    finally {
      commands.remove(this)
      release()
    }
  }

  protected open fun onSuspendOrFinish() {
  }

  protected open fun invokeContinuation() {
    executeContinuation()
  }

  private fun getOrCreateCommandScope(parentScope: CoroutineScope): CoroutineScope =
    myCoroutineScope ?: parentScope.childScope("Debugger Command $this", this).also { myCoroutineScope = it }

  private fun cancelCommandScope() {
    myCoroutineScope?.cancel()
    myCoroutineScope = null
  }

  internal fun resetContinuation(runnable: Runnable?): Runnable? = continuation.getAndSet(runnable)
  protected fun executeContinuation() {
    resetContinuation(null)?.run()
  }

  internal fun invokeCommand(dispatcher: DebuggerThreadDispatcher, parentScope: CoroutineScope) {
    if (continuation.get() == null) {
      var exception: Exception? = null
      val commandScope = getOrCreateCommandScope(parentScope)
      // executed synchronously until the first suspend, resume is handled by dispatcher
      commandScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
        try {
          if (commandScope.isActive) {
            try {
              runSuspend()
            }
            catch (e: Exception) {
              exception = e
            }
          }
          else {
            notifyCancelled()
          }
        } finally {
          // Command finished or postponed
          cancelCommandScope()
        }
      }
      onSuspendOrFinish()
      exception?.let { throw it }
    }
    else {
      invokeContinuation()
    }
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
