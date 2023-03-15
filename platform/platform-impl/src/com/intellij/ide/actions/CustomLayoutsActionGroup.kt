// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager

class CustomLayoutsActionGroup : ActionGroup(), DumbAware {

  private val childrenCache = NamedLayoutListBasedCache<AnAction> {
    CustomLayoutActionGroup(it)
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = childrenCache.getCachedOrUpdatedArray(AnAction.EMPTY_ARRAY)

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  private class CustomLayoutActionGroup(
    @NlsSafe private val layoutName: String
  ) : ActionGroup(ActionsBundle.message("group.CustomLayoutActionsGroup.text"), true), DumbAware {

    private val children = arrayOf<AnAction>(
      Apply(layoutName),
      RenameLayoutAction(layoutName),
      Separator(),
      Delete(layoutName),
    )

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.text = if (manager.activeLayoutName == layoutName)
        ActionsBundle.message("group.CustomLayoutActionsGroup.current.text", layoutName)
      else
        layoutName
      e.presentation.isVisible = layoutName.isNotBlank() // Just in case the layout name is corrupted somehow.
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> = children

    private class Apply(private val layoutName: String) : DumbAwareAction() {
      init {
        templatePresentation.text = ActionsBundle.message("action.CustomLayoutActionsGroup.Apply.text")
      }

      override fun getActionUpdateThread() = ActionUpdateThread.BGT

      override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val layoutManager = ToolWindowDefaultLayoutManager.getInstance()
        layoutManager.activeLayoutName = layoutName
        ToolWindowManagerEx.getInstanceEx(project).setLayout(layoutManager.getLayoutCopy())
      }

      override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = manager.activeLayoutName != layoutName
        e.presentation.description = ActionsBundle.message("action.RestoreNamedLayout.description", layoutName)
      }

    }

    private class Delete(layoutName: String) : DeleteNamedLayoutAction(layoutName) {
      init {
        templatePresentation.text = ActionsBundle.message("action.CustomLayoutActionsGroup.Delete.text")
      }
    }

  }

}

private val manager get() = ToolWindowDefaultLayoutManager.getInstance()
