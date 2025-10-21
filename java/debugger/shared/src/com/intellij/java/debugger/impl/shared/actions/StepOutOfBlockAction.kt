// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.actions

import com.intellij.java.debugger.impl.shared.SharedJavaDebuggerSession
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionApi
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import kotlinx.coroutines.launch

private class StepOutOfBlockAction : AnAction(), DumbAware, ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun actionPerformed(e: AnActionEvent) {
    val sessionProxy = DebuggerUIUtil.getSessionProxy(e) ?: return
    sessionProxy.coroutineScope.launch {
      JavaDebuggerSessionApi.getInstance().stepOutOfCodeBlock(sessionProxy.id)
    }
  }

  override fun update(e: AnActionEvent) {
    val sessionProxy = DebuggerUIUtil.getSessionProxy(e)
    val javaSession = SharedJavaDebuggerSession.findSession(e)
    e.presentation.isEnabledAndVisible = javaSession != null && sessionProxy != null
                                         && !sessionProxy.isReadOnly && sessionProxy.isSuspended
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
