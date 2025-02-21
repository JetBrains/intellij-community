// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.openapi.application.readAction
import com.intellij.xdebugger.XSourcePosition

internal fun scheduleSourcePositionCompute(
  evaluationContext: EvaluationContextImpl,
  descriptor: ValueDescriptorImpl,
  inline: Boolean,
  positionCallback: (XSourcePosition?) -> Unit,
) {
  evaluationContext.managerThread.schedule(object : SuspendContextCommandImpl(evaluationContext.suspendContext) {
    override fun getPriority() = if (inline) PrioritizedTask.Priority.LOWEST else PrioritizedTask.Priority.NORMAL

    override fun commandCancelled() {
      positionCallback(null)
    }

    override suspend fun contextActionSuspend(suspendContext: SuspendContextImpl) {
      val debugContext = suspendContext.debugProcess.debuggerContext
      val position = SourcePositionProvider.getSourcePosition(descriptor, descriptor.project, debugContext, false)
      readAction { positionCallback(DebuggerUtilsEx.toXSourcePosition(position)) }
      if (inline) {
        val inlinePosition = SourcePositionProvider.getSourcePosition(descriptor, descriptor.project, debugContext, true)
        if (inlinePosition != null) {
          readAction { positionCallback(DebuggerUtilsEx.toXSourcePosition(inlinePosition)) }
        }
      }
    }
  })
}