// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.openapi.application.readAction
import com.intellij.xdebugger.XSourcePosition
import kotlinx.coroutines.InternalCoroutinesApi
import kotlin.coroutines.cancellation.CancellationException

@OptIn(InternalCoroutinesApi::class)
internal fun scheduleSourcePositionCompute(
  evaluationContext: EvaluationContextImpl,
  descriptor: ValueDescriptorImpl,
  inline: Boolean,
  positionCallback: (XSourcePosition?) -> Unit,
) {
  val suspendContext = evaluationContext.suspendContext
  val priority = if (inline) PrioritizedTask.Priority.LOWEST else PrioritizedTask.Priority.NORMAL
  executeOnDMT(suspendContext, priority) {
    val debugContext = suspendContext.debugProcess.debuggerContext
    val position = SourcePositionProvider.getSourcePosition(descriptor, descriptor.project, debugContext, false)
    readAction { positionCallback(DebuggerUtilsEx.toXSourcePosition(position)) }
    if (inline) {
      val inlinePosition = SourcePositionProvider.getSourcePosition(descriptor, descriptor.project, debugContext, true)
      if (inlinePosition != null) {
        readAction { positionCallback(DebuggerUtilsEx.toXSourcePosition(inlinePosition)) }
      }
    }
  }.invokeOnCompletion {
    if (it is CancellationException) {
      positionCallback(null)
    }
  }
}