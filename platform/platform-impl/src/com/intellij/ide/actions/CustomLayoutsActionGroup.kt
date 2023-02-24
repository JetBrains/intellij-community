// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsSafe
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager

class CustomLayoutsActionGroup : ActionGroup(), DumbAware {

  private val childrenCache = NamedLayoutListBasedCache<AnAction> {
    CustomLayoutActionGroup(it)
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = childrenCache.getCachedOrUpdatedArray(AnAction.EMPTY_ARRAY)

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  private class CustomLayoutActionGroup(@NlsSafe private val layoutName: String) : ActionGroup(), DumbAware {

    private val children = arrayOf<AnAction>(
      Apply(layoutName),
      Separator(),
      Delete(layoutName),
    )

    init {
      templatePresentation.isPopupGroup = true
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      val manager = ToolWindowDefaultLayoutManager.getInstance()
      e.presentation.text = if (manager.activeLayoutName == layoutName)
        ActionsBundle.message("group.CustomLayoutActionsGroup.current.text", layoutName)
      else
        layoutName
      e.presentation.isVisible = layoutName.isNotBlank() // Just in case the layout name is corrupted somehow.
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> = children

    private class Apply(layoutName: String) : RestoreNamedLayoutAction(layoutName) {
      init {
        templatePresentation.text = ActionsBundle.message("action.CustomLayoutActionsGroup.Apply.text")
      }

      override fun isSelected(e: AnActionEvent): Boolean = false // no check mark needed in this submenu
    }

    private class Delete(layoutName: String) : DeleteNamedLayoutAction(layoutName) {
      init {
        templatePresentation.text = ActionsBundle.message("action.CustomLayoutActionsGroup.Delete.text")
      }
    }

  }

}
