// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.actions

import com.intellij.idea.ActionsBundle
import com.intellij.java.debugger.impl.shared.SharedJavaDebuggerSession
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerLuxActionsApi
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import kotlinx.coroutines.launch

private class CustomizeContextViewAction : XDebuggerTreeActionBase(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun actionPerformed(e: AnActionEvent) {
    perform(null, "", e)
  }

  override fun perform(node: XValueNodeImpl?, nodeName: String, e: AnActionEvent) {
    val session = DebuggerUIUtil.getSessionProxy(e) ?: return
    session.coroutineScope.launch {
      JavaDebuggerLuxActionsApi.getInstance().showCustomizeDataViewsDialog(session.project.projectId())
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.setText(ActionsBundle.actionText("Debugger.CustomizeContextView"))
    val javaSession = SharedJavaDebuggerSession.findSession(e)
    e.presentation.setEnabledAndVisible(javaSession != null)
  }
}
