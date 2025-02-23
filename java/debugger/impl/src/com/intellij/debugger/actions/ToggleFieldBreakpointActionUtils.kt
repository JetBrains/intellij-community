// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.SourcePositionProvider.Companion.getSourcePosition
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl

internal fun getSourcePositionNow(
  managerThread: DebuggerManagerThreadImpl,
  debuggerContext: DebuggerContextImpl,
  descriptor: NodeDescriptorImpl,
): SourcePosition? {
  var position : SourcePosition? = null
  managerThread.invokeAndWait(object : DebuggerContextCommandImpl(debuggerContext) {
    override fun getPriority() = PrioritizedTask.Priority.HIGH

    override suspend fun threadActionSuspend(suspendContext: SuspendContextImpl) {
      position = getSourcePosition(descriptor, suspendContext.debugProcess.project, debuggerContext)
    }
  })
  return position
}
