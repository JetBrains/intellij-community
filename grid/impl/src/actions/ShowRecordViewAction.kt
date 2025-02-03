package com.intellij.database.actions

import com.intellij.database.datagrid.GridUtil
import com.intellij.database.run.ui.*
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ShowRecordViewAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val grid = GridUtil.getDataGrid(e.dataContext)
    e.presentation.isEnabledAndVisible = grid != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val grid = GridUtil.getDataGrid(e.dataContext)
    if (grid == null) {
      return
    }
    val view = ShowEditMaximizedAction.getView(grid, e)
    view.open { it is RecordViewInfoProvider }

    if (grid.isEditable) {
      val focusComponent = view.preferedFocusComponent
      focusComponent?.requestFocus()
    }
  }
}