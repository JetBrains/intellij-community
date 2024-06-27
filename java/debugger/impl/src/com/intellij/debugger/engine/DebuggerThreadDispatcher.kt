// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

internal class DebuggerThreadDispatcher(private val managerThread: DebuggerManagerThreadImpl) : CoroutineDispatcher() {
  override fun dispatch(context: CoroutineContext, block: Runnable) {
    val debuggerCommand = context[DebuggerCommandImpl.KEY] ?: object : DebuggerCommandImpl(PrioritizedTask.Priority.LOW) {
      override fun action() = error("Should not be called")
    }
    val value = debuggerCommand.resetContinuation(block)
    check(value == null) { "Continuation is already set $value" }
    managerThread.schedule(debuggerCommand)
  }

  override fun isDispatchNeeded(context: CoroutineContext) = !DebuggerManagerThreadImpl.isManagerThread()
}
