// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.actions

import com.intellij.java.debugger.impl.shared.JavaDebuggerSharedBundle
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionApi
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import kotlinx.coroutines.launch

private class InterruptThreadAction : XDebuggerTreeActionBase(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun perform(node: XValueNodeImpl?, nodeName: String, e: AnActionEvent) {
    if (node == null) return
    val project = e.project
    if (project == null) {
      return
    }
    val sessionProxy = DebuggerUIUtil.getSessionProxy(e) ?: return
    if (!node.isSuspendedJavaThread()) {
      sessionProxy.coroutineScope.launch {
        val executionStack = node.executionStackOrNull ?: return@launch
        XDebugManagerProxy.getInstance().withId(executionStack, sessionProxy) { xExecutionStackId ->
          JavaDebuggerSessionApi.getInstance().interruptThread(xExecutionStackId)
        }
        sessionProxy.rebuildViews()
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val selectedNodes = getSelectedNodes(e.dataContext)
    e.presentation.apply {
      isEnabledAndVisible = false
      if (DebuggerUIUtil.getSessionProxy(e) == null) {
        return
      }
      if (selectedNodes.isNotEmpty() && selectedNodes.all { it.isNotSuspendedJavaThread() }) {
        text = JavaDebuggerSharedBundle.message("action.Debugger.XThreadsView.InterruptThread.text")
        isEnabledAndVisible = true
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}