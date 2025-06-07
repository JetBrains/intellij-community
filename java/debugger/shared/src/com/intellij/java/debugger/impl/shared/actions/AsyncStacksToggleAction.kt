// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.actions

import com.intellij.java.debugger.impl.shared.SharedJavaDebuggerManager
import com.intellij.java.debugger.impl.shared.SharedJavaDebuggerSession
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionApi
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AsyncStacksToggleAction : DumbAwareToggleAction(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun isSelected(e: AnActionEvent): Boolean {
    return getJavaSession(e)?.isAsyncStacksEnabled ?: true
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    getJavaSession(e)?.isAsyncStacksEnabled = state
    DebuggerUIUtil.getSessionProxy(e)?.apply {
      AsyncStackTraceActionCoroutineScope.getInstance(project).cs.launch {
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

private fun getJavaSession(e: AnActionEvent) = DebuggerUIUtil.getSessionProxy(e)?.let(::getJavaSession)

private fun getJavaSession(proxy: XDebugSessionProxy): SharedJavaDebuggerSession? = SharedJavaDebuggerManager.getInstance(proxy.project).getJavaSession(proxy.id)

@Suppress("OPT_IN_USAGE")
@Service(Service.Level.PROJECT)
internal class AsyncStackTraceActionCoroutineScope(val cs: CoroutineScope) {

  companion object {
    fun getInstance(project: Project): AsyncStackTraceActionCoroutineScope = project.service()
  }
}
