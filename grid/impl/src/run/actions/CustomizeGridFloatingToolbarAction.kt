package com.intellij.database.run.actions

import com.intellij.database.datagrid.GridUtil
import com.intellij.database.run.ui.table.TableFloatingToolbar
import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class CustomizeGridFloatingToolbarAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    val grid = GridUtil.getDataGrid(e.dataContext) ?: return
    val settings = GridUtil.getSettings(grid) ?: return
    e.presentation.isEnabledAndVisible = !settings.isDisableGridFloatingToolbar && settings.isFloatingToolbarCustomizable
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dialogWrapper = CustomizationUtil.createCustomizeGroupDialog(
      project,
      TableFloatingToolbar.ACTION_GROUP_ID,
      TableFloatingToolbar.actionGroupTitle,
      null
    )
    dialogWrapper.show()
  }
}