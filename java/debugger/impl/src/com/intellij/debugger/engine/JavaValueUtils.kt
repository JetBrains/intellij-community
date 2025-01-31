// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.openapi.application.readAction
import com.intellij.xdebugger.frame.XNavigatable

internal fun scheduleSourcePositionCompute(
  evaluationContext: EvaluationContextImpl,
  descriptor: ValueDescriptorImpl,
  navigatable: XNavigatable,
  inline: Boolean,
) {
  evaluationContext.managerThread.schedule(object : SuspendContextCommandImpl(evaluationContext.suspendContext) {
    override fun getPriority() = if (inline) PrioritizedTask.Priority.LOWEST else PrioritizedTask.Priority.NORMAL

    override fun commandCancelled() {
      navigatable.setSourcePosition(null)
    }

    override suspend fun contextActionSuspend(suspendContext: SuspendContextImpl) {
      val debugContext = evaluationContext.debugProcess.debuggerContext
      if (inline) {
        val inlinePosition = SourcePositionProvider.getSourcePosition(descriptor, descriptor.project, debugContext, true)
        if (inlinePosition != null) {
          readAction { navigatable.setSourcePosition(DebuggerUtilsEx.toXSourcePosition(inlinePosition)) }
          return
        }
      }
      val position = SourcePositionProvider.getSourcePosition(descriptor, descriptor.project, debugContext, false)
      readAction { navigatable.setSourcePosition(DebuggerUtilsEx.toXSourcePosition(position)) }
    }
  })
}