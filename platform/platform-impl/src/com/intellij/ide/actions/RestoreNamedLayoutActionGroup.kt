// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager

class RestoreNamedLayoutActionGroup : ActionGroup(), DumbAware {

  private val childrenCache = NamedLayoutListBasedCache<AnAction>(
    listOf(ActionManager.getInstance().getAction(RestoreFactoryDefaultLayoutAction.ID)), 1
  ) {
    RestoreNamedLayoutAction(it)
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = childrenCache.getCachedOrUpdatedArray(AnAction.EMPTY_ARRAY)

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private class RestoreNamedLayoutAction(@NlsSafe private val layoutName: String) : DumbAwareToggleAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val project = e.project ?: return
      val layoutManager = ToolWindowDefaultLayoutManager.getInstance()
      layoutManager.activeLayoutName = layoutName
      ToolWindowManagerEx.getInstanceEx(project).setLayout(layoutManager.getLayoutCopy())
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabled = e.project != null
      e.presentation.setText({ layoutName }, false)
      e.presentation.description = ActionsBundle.message("action.RestoreNamedLayout.description", layoutName)
    }

    override fun isSelected(e: AnActionEvent): Boolean =
      ToolWindowDefaultLayoutManager.getInstance().activeLayoutName == layoutName

  }

}
