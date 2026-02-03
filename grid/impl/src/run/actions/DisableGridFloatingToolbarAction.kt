package com.intellij.database.run.actions

import com.intellij.database.datagrid.GridUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class DisableGridFloatingToolbarAction : DumbAwareAction(), GridAction {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    val grid = GridUtil.getDataGrid(e.dataContext) ?: return
    val settings = GridUtil.getSettings(grid) ?: return
    e.presentation.isEnabledAndVisible = !settings.isDisableGridFloatingToolbar
  }

  override fun actionPerformed(e: AnActionEvent) {
    val grid = GridUtil.getDataGrid(e.dataContext) ?: return
    val settings = GridUtil.getSettings(grid) ?: return
    settings.isDisableGridFloatingToolbar = true
    settings.fireChanged()
  }
}