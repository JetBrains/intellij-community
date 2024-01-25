// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.ui.UIBundle

/**
 * @author Alexander Lobas
 */
class ToolWindowShowNamesAction : DumbAwareToggleAction(UIBundle.message("tool.window.show.names")) {
  override fun isSelected(e: AnActionEvent): Boolean {
    return ToolWindowManagerEx.getInstanceEx(e.project ?: return false).isShowNames()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    ToolWindowManagerEx.getInstanceEx(e.project ?: return).setShowNames(state)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}