package com.intellij.database.actions

import com.intellij.database.datagrid.GridUtil
import com.intellij.database.run.ui.*
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

class ToggleEditMaximizedViewAction : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val grid = GridUtil.getDataGrid(e.dataContext)
    e.presentation.isEnabledAndVisible = grid != null
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return findEditMaximized(e.dataContext) != null
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val grid = GridUtil.getDataGrid(e.dataContext)
    if (grid == null) {
      return
    }
    val view = ShowEditMaximizedAction.getView(grid, e)
    if (state) {
      view.open { it is RecordViewInfoProvider }
      if (grid.isEditable) {
        val focusComponent = view.preferedFocusComponent
        focusComponent?.requestFocus()
      }
    }
    else {
      HideEditMaximizedAction.hideValueEditor(grid, view)
    }
  }
}