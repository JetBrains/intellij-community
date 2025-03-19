package com.intellij.database.run.actions

import com.intellij.database.DatabaseDataKeys
import com.intellij.database.datagrid.*
import com.intellij.database.datagrid.GridUtil.addBottomHeader
import com.intellij.database.run.ui.DataGridRequestPlace
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key

val SHOW_PAGINATION = Key.create<Boolean>("SHOW_PAGINATION")

class ShowPaginationAction : ToggleAction(), DumbAware, GridAction {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY)
    e.presentation.isEnabledAndVisible = grid != null
    super.update(e)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY) ?: return false
    return grid.panel.bottomHeaderComponent != null && SHOW_PAGINATION[grid] == true
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY) ?: return
    enablePagination(grid, state, e)
    val helper = GridHelper.get(grid)
    if (helper.isLoadWholeTableWhenPaginationIsOff(grid)) {
      val source = GridRequestSource(DataGridRequestPlace(grid))
      if (state) {
        setPageSize(grid.dataHookup, helper)
        grid.dataHookup.loader.reloadCurrentPage(source)
      }
      else {
        grid.dataHookup.pageModel.pageSize = GridPagingModel.UNLIMITED_PAGE_SIZE
        grid.dataHookup.loader.load(source, 0)
      }
    }
  }
}

fun enablePagination(grid: DataGrid, state: Boolean, e: AnActionEvent? = null) {
  SHOW_PAGINATION[grid] = state
  if (state) {
    if (grid.panel.bottomHeaderComponent == null) {
      addBottomHeader(grid)
      grid.getUserData(RESULTS_PATCHER)?.updateHeight()
    }
  }
}
