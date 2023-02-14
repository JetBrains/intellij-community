// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions

import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil

class ForceOnDemandRenderersAction : ToggleAction(), DumbAware {

  override fun isSelected(e: AnActionEvent): Boolean {
    return RENDERERS_ONDEMAND_FORCED.get(DebuggerUIUtil.getSessionData(e), false)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    RENDERERS_ONDEMAND_FORCED.set(DebuggerUIUtil.getSessionData(e), state)
    NodeRendererSettings.getInstance().fireRenderersChanged()
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = DebuggerAction.isInJavaSession(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  companion object {
    private val RENDERERS_ONDEMAND_FORCED = Key.create<Boolean>("RENDERERS_ONDEMAND_FORCED")

    @JvmStatic
    fun isForcedOnDemand(session: XDebugSessionImpl): Boolean {
      return RENDERERS_ONDEMAND_FORCED.get(session.sessionData, false)
    }
  }
}
