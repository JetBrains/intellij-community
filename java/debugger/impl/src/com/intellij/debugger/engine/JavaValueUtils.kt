// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.actions.JavaReferringObjectsValue
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.render.NodeRenderer
import com.intellij.debugger.ui.tree.render.Renderer
import com.intellij.java.debugger.impl.shared.engine.JavaValueDescriptor
import com.intellij.java.debugger.impl.shared.engine.JavaValueObjectReferenceInfo
import com.intellij.java.debugger.impl.shared.engine.NodeRendererDto
import com.intellij.java.debugger.impl.shared.engine.NodeRendererId
import com.intellij.openapi.application.readAction
import com.intellij.platform.debugger.impl.shared.FrontendDescriptorStateManager
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XDescriptor
import com.sun.jdi.ObjectReference
import fleet.rpc.core.toRpc
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture
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

internal fun getJavaValueXDescriptor(javaValue: JavaValue): CompletableFuture<XDescriptor> {
  val valueDescriptor = javaValue.descriptor

  val suspendContext = javaValue.evaluationContext.suspendContext
  val cs = suspendContext.coroutineScope
  return cs.future {
    var objectReferenceInfo: JavaValueObjectReferenceInfo? = null
    withDebugContext(suspendContext) {
      val value = valueDescriptor.getValue()
      if (value is ObjectReference) {
        objectReferenceInfo = JavaValueObjectReferenceInfo(value.referenceType().name(), value.virtualMachine().canGetInstanceInfo())
      }
    }
    val renderersUpdatedFlow = javaValue.evaluationContext.debugProcess.renderersUpdatedFlow
    val xDescriptor = JavaValueDescriptor(
      valueDescriptor.isString(),
      objectReferenceInfo,
      valueDescriptor.lastRenderer?.toRpc(),
      valueDescriptor.lastRendererFlow.map { it?.toRpc() }.toRpc(),
      renderersUpdatedFlow.map { fetchApplicableNodeRenderers(javaValue).map { it.toRpc() } }.toRpc()
    )
    // for actions to work in monolith
    FrontendDescriptorStateManager.getInstance(valueDescriptor.project).registerDescriptor(xDescriptor, cs)
    xDescriptor
  }
}

private suspend fun fetchApplicableNodeRenderers(javaValue: JavaValue): List<NodeRenderer> {
  val renderersFuture = withDebugContext(javaValue.evaluationContext.suspendContext) {
    getApplicableNodeRenderers(javaValue)
  }
  return renderersFuture.await()
}

private fun getApplicableNodeRenderers(value: JavaValue): CompletableFuture<List<NodeRenderer>> {
  if (value is JavaReferringObjectsValue) { // disable for any referrers at all
    return CompletableFuture.completedFuture(emptyList())
  }
  val valueDescriptor = value.descriptor
  if (!valueDescriptor.isValueValid) {
    return CompletableFuture.completedFuture(emptyList())
  }
  val process = value.evaluationContext.debugProcess
  return process.getApplicableRenderers(valueDescriptor.getType())
}

private fun Renderer.toRpc() = (this as? NodeRenderer)?.toRpc()
private fun NodeRenderer.toRpc() = NodeRendererDto(id, name)
internal val NodeRenderer.id: NodeRendererId get() = NodeRendererId(System.identityHashCode(this))
