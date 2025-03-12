// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

internal class DebuggerThreadDispatcher(private val managerThread: DebuggerManagerThreadImpl) : CoroutineDispatcher() {
  override fun dispatch(context: CoroutineContext, block: Runnable) {
    val debuggerCommand = getOrCreateDebuggerCommand(context)
    val value = debuggerCommand.resetContinuation(block)
    check(value == null) { "Continuation is already set $value" }
    managerThread.schedule(debuggerCommand)
  }

  private fun getOrCreateDebuggerCommand(context: CoroutineContext): DebuggerCommandImpl {
    val existingCommand = context[DebuggerCommandImpl.KEY]
    if (existingCommand != null) return existingCommand

    val suspendContext = context[SuspendContextImpl.Key]
    val priority = context[PrioritizedTask.Priority] ?: PrioritizedTask.Priority.LOW
    return if (suspendContext == null) {
      object : DebuggerCommandImpl(priority) {
        override fun action() = error("Should not be called")
      }
    }
    else {
      object : SuspendContextCommandImpl(suspendContext) {
        override val priority get() = priority
        override fun contextAction(suspendContext: SuspendContextImpl) = error("Should not be called")
      }
    }.apply {
      setCancellationAction { context.cancel() }
    }
  }
}

@Suppress("FunctionName", "UnusedReceiverParameter")
internal fun Dispatchers.Debugger(managerThread: DebuggerManagerThreadImpl): CoroutineDispatcher = managerThread.debuggerThreadDispatcher
