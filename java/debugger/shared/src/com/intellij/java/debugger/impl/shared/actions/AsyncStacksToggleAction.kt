// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.actions

import com.intellij.java.debugger.impl.shared.SharedJavaDebuggerSession
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionApi
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.platform.debugger.impl.shared.SplitDebuggerAction
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import kotlinx.coroutines.launch

class AsyncStacksToggleAction : DumbAwareToggleAction(), SplitDebuggerAction {
  override fun isSelected(e: AnActionEvent): Boolean {
    return getJavaSession(e)?.isAsyncStacksEnabled ?: true
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    getJavaSession(e)?.isAsyncStacksEnabled = state
    DebuggerUIUtil.getSessionProxy(e)?.apply {
      e.coroutineScope.launch {
        JavaDebuggerSessionApi.getInstance().setAsyncStacksEnabled(id, state)
      }
      if (isSuspended) {
        rebuildViews()
      }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = getJavaSession(e) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

private fun getJavaSession(e: AnActionEvent) = SharedJavaDebuggerSession.findSession(e)
