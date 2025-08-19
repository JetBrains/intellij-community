// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.actions

import com.intellij.java.debugger.impl.shared.SharedJavaDebuggerSession
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionApi
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import kotlinx.coroutines.launch

internal class MuteRenderersAction : ToggleAction(), DumbAware, ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {

  override fun isSelected(e: AnActionEvent): Boolean {
    val javaSession = SharedJavaDebuggerSession.findSession(e) ?: return false
    return javaSession.areRenderersMuted
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val javaSession = SharedJavaDebuggerSession.findSession(e) ?: return
    val session = DebuggerUIUtil.getSessionProxy(e) ?: return
    javaSession.areRenderersMuted = state
    session.coroutineScope.launch {
      JavaDebuggerSessionApi.getInstance().muteRenderers(session.id, state)
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val javaSession = SharedJavaDebuggerSession.findSession(e)
    e.presentation.isEnabledAndVisible = javaSession != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}