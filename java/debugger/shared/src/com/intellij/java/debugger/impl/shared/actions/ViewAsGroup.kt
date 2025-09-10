// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.actions

import com.intellij.java.debugger.impl.shared.engine.JavaValueDescriptor
import com.intellij.java.debugger.impl.shared.engine.JavaValueDescriptorState
import com.intellij.java.debugger.impl.shared.engine.NodeRendererDto
import com.intellij.java.debugger.impl.shared.engine.NodeRendererId
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionApi
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.shared.FrontendDescriptorStateManager
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import kotlinx.coroutines.launch

internal class ViewAsGroup : ActionGroup(Presentation.NULL_STRING, true), DumbAware, ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private class RendererAction(private val nodeRenderer: NodeRendererDto) : ToggleAction(nodeRenderer.name), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
    init {
      getTemplatePresentation().setKeepPopupOnPerform(KeepPopupOnPerform.IfRequested)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      val project = e.project ?: return false
      val descriptors = getSelectedJavaValueDescriptors(e)
      if (descriptors.isEmpty()) {
        return false
      }
      for (descriptor in descriptors) {
        val descriptorState = descriptor.getState(project) ?: return false
        if (descriptorState.lastRenderer?.id != nodeRenderer.id) {
          return false
        }
      }
      return true
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (!state) return
      setRendererForNodes(e, nodeRenderer.id)
    }
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.project ?: return EMPTY_ARRAY

    val values = getSelectedJavaValueDescriptors(e)
    if (values.isEmpty()) return EMPTY_ARRAY
    val rs = getApplicableRenderers(project, values)
    if (rs.isEmpty()) return EMPTY_ARRAY
    val children = mutableListOf<AnAction>()
    val actionManager = ActionManager.getInstance()
    children.add(actionManager.getAction("Debugger.CreateRenderer"))
    if (rs.size > 1) {
      children.add(Separator.getInstance())
      children.add(actionManager.getAction("Debugger.AutoRenderer"))
    }

    if (!children.isEmpty()) {
      children.add(Separator.getInstance())
    }
    children.addAll(rs.map { nodeRenderer -> RendererAction(nodeRenderer) })
    return children.toTypedArray()
  }

  override fun update(event: AnActionEvent) {
    event.presentation.setEnabledAndVisible(getSelectedJavaValuesWithDescriptors(event).isNotEmpty())
  }
}

private fun getApplicableRenderers(project: Project, values: List<JavaValueDescriptor>): List<NodeRendererDto> {
  var res: MutableList<NodeRendererDto>? = null
  for (descriptor in values) {
    val state = descriptor.getState(project) ?: return emptyList()
    val list = state.applicableRenderers
    if (res == null) {
      res = list.toMutableList()
    }
    else {
      res.retainAll(list)
    }
  }
  return res ?: emptyList()
}

internal fun getSelectedJavaValuesWithDescriptors(event: AnActionEvent): List<Triple<XValueNodeImpl, XValue, JavaValueDescriptor>> {
  val selectedNodes = XDebuggerTree.getSelectedNodes(event.dataContext)
  return selectedNodes.map { it to it.valueContainer }
    .map { it to it.second.xValueDescriptorAsync?.getNow(null) }
    .filter { it.second is JavaValueDescriptor }
    .map { Triple(it.first.first, it.first.second, it.second as JavaValueDescriptor) }
}

private fun getSelectedJavaValueDescriptors(event: AnActionEvent): List<JavaValueDescriptor> {
  return getSelectedJavaValuesWithDescriptors(event).map { it.third }
}

internal fun setRendererForNodes(e: AnActionEvent, rendererId: NodeRendererId?) {
  val session = DebuggerUIUtil.getSessionProxy(e) ?: return
  val selectedNodesWithJavaValues = getSelectedJavaValuesWithDescriptors(e).map { it.first to it.second }

  session.coroutineScope.launch {
    val xValues = selectedNodesWithJavaValues.map { it.second }
    xValues.withId(session) { ids ->
      JavaDebuggerSessionApi.getInstance().setRenderer(rendererId, ids)
    }
    for ((node, value) in selectedNodesWithJavaValues) {
      node.invokeNodeUpdate {
        node.clearChildren()
        value.computePresentation(node, XValuePlace.TREE)
      }
    }
  }
}

private fun JavaValueDescriptor.getState(project: Project): JavaValueDescriptorState? =
  FrontendDescriptorStateManager.getInstance(project).getState(this) as? JavaValueDescriptorState

internal suspend fun <T> List<XValue>.withId(session: XDebugSessionProxy, block: suspend (List<XValueId>) -> T): T {
  val ids = mutableListOf<XValueId>()
  val managerProxy = XDebugManagerProxy.getInstance()
  suspend fun rec(i: Int): T {
    return if (i < size) {
      managerProxy.withId(this[i], session) { id ->
        ids.add(id)
        rec(i + 1)
      }
    }
    else {
      block(ids)
    }
  }

  return rec(0)
}


