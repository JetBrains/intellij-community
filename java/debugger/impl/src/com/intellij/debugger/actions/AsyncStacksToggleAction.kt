// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil

class AsyncStacksToggleAction : DumbAwareToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    return ASYNC_STACKS_ENABLED.get(DebuggerUIUtil.getSessionData(e), true)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    ASYNC_STACKS_ENABLED.set(DebuggerUIUtil.getSessionData(e), state)
    DebuggerUIUtil.getSessionProxy(e)?.apply {
      if (isSuspended) {
        rebuildViews()
      }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = DebuggerAction.isInJavaSession(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  companion object {
    private val ASYNC_STACKS_ENABLED = Key.create<Boolean>("ASYNC_STACKS_ENABLED")

    @JvmStatic
    fun isAsyncStacksEnabled(session: XDebugSessionImpl): Boolean {
      return ASYNC_STACKS_ENABLED.get(session.sessionData, true)
    }

    @JvmStatic
    fun setAsyncStacksEnabled(session: XDebugSessionImpl, state: Boolean) {
      ASYNC_STACKS_ENABLED.set(session.sessionData, state)
    }
  }
}
