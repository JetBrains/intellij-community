// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.actions

import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerLuxActionsApi
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import kotlinx.coroutines.launch

private class CreateRendererAction : AnAction(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun update(e: AnActionEvent) {
    val values = getSelectedJavaValuesWithDescriptors(e)
    e.presentation.setEnabledAndVisible(values.size == 1)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val session = DebuggerUIUtil.getSessionProxy(e) ?: return
    val (_, xValue, _) = getSelectedJavaValuesWithDescriptors(e).singleOrNull() ?: return
    session.coroutineScope.launch {
      XDebugManagerProxy.getInstance().withId(xValue, session) { xValueId ->
        JavaDebuggerLuxActionsApi.getInstance().showCreateRendererDialog(xValueId)
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
