// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.events.DebugContextElement
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

internal class DebuggerThreadDispatcher(private val managerThread: DebuggerManagerThreadImpl) : CoroutineDispatcher() {
  override fun dispatch(context: CoroutineContext, block: Runnable) {
    val debuggerCommand = createCommand(context, block)
    managerThread.schedule(debuggerCommand)
  }

  private fun createCommand(context: CoroutineContext, block: Runnable): DebuggerCommandImpl {
    val suspendContext = context[SuspendContextImpl.Key]
    val debugContext = context[DebugContextElement.Key]?.context
    val priority = context[PrioritizedTask.Priority] ?: PrioritizedTask.Priority.LOW

    fun cancelCommand() {
      context.cancel()
      // TODO
      // Another important use-case is cancellation of tasks scheduled from [com.intellij.debugger.engine.DebuggerThreadDispatcher]:
      // the dispatcher must guarantee to call the passed action, but we could not do so because the context is already resumed.
      // In this case, we cancel the [Job] first, and then call the passed action.
      // For example, a command can start a computation in [kotlinx.coroutines.Dispatchers.Default].
      // When the program is resumed, the job will be canceled.
      block.run()
    }

    return when {
      suspendContext != null -> object : SuspendContextCommandImpl(suspendContext) {
        override val priority get() = priority
        override fun contextAction(suspendContext: SuspendContextImpl) = block.run()
        override fun commandCancelled() = cancelCommand()
      }
      debugContext != null -> object : DebuggerContextCommandImpl(debugContext) {
        override val priority get() = priority
        override fun threadAction(suspendContext: SuspendContextImpl) = block.run()
        override fun commandCancelled() = cancelCommand()
      }
      else -> object : DebuggerCommandImpl(priority) {
        override fun action() = block.run()
        override fun commandCancelled() = cancelCommand()
      }
    }
  }
}

@Suppress("FunctionName", "UnusedReceiverParameter")
internal fun Dispatchers.Debugger(managerThread: DebuggerManagerThreadImpl): CoroutineDispatcher = managerThread.debuggerThreadDispatcher
