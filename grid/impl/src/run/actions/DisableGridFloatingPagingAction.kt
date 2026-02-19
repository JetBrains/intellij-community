package com.intellij.database.run.actions

import com.intellij.database.datagrid.GridUtil
import com.intellij.database.settings.DataGridSettings.PagingDisplayMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

abstract class MoveGridFloatingPagingAction(val newPosition: PagingDisplayMode) : DumbAwareAction(), GridAction {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    val grid = GridUtil.getDataGrid(e.dataContext) ?: return
    val settings = GridUtil.getSettings(grid) ?: return
    e.presentation.isEnabledAndVisible = settings.pagingDisplayMode != newPosition
  }

  override fun actionPerformed(e: AnActionEvent) {
    val grid = GridUtil.getDataGrid(e.dataContext) ?: return
    val settings = GridUtil.getSettings(grid) ?: return
    settings.pagingDisplayMode = newPosition
    settings.fireChanged()
  }

  class EditorToolbar : MoveGridFloatingPagingAction(PagingDisplayMode.DATA_EDITOR_TOOLBAR)
  class GridLeft : MoveGridFloatingPagingAction(PagingDisplayMode.GRID_LEFT_FLOATING)
  class GridRight : MoveGridFloatingPagingAction(PagingDisplayMode.GRID_RIGHT_FLOATING)
  class GridCenter : MoveGridFloatingPagingAction(PagingDisplayMode.GRID_CENTER_FLOATING)
}