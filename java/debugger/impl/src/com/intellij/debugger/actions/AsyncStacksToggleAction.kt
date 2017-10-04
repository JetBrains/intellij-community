// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions

import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil

/**
 * @author egor
 */
class AsyncStacksToggleAction : ToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    return ASYNC_STACKS_ENABLED.get(DebuggerUIUtil.getSessionData(e), true)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    ASYNC_STACKS_ENABLED.set(DebuggerUIUtil.getSessionData(e), state)
    DebuggerAction.refreshViews(e.getData(XDebugSession.DATA_KEY))
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = DebuggerUtilsEx.isInJavaSession(e)
  }

  companion object {
    private val ASYNC_STACKS_ENABLED = Key.create<Boolean>("ASYNC_STACKS_ENABLED")

    @JvmStatic
    fun isAsyncStacksEnabled(session: XDebugSessionImpl): Boolean {
      return ASYNC_STACKS_ENABLED.get(session.sessionData, true)
    }
  }
}
