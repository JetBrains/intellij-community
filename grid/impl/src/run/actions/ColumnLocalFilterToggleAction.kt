package com.intellij.database.run.actions

import com.intellij.database.DatabaseDataKeys
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.run.ui.table.TableResultView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware

class ColumnLocalFilterToggleAction : ToggleAction(), DumbAware, GridAction,
                                      ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {

  override fun update(e: AnActionEvent) {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY)

    e.presentation.isEnabled =
      grid != null &&
      LocalFilterActionUtils.isGridInNonTransposedTableMode(grid)

    super.update(e)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val grid: DataGrid? = e.getData(DatabaseDataKeys.DATA_GRID_KEY)
    return grid != null && grid.resultView is TableResultView && grid.localFilterState.isEnabled
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val grid: DataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY) ?: return
    val view = grid.resultView as? TableResultView
    if (view != null) {
      grid.localFilterState.isEnabled = state
      view.onLocalFilterStateChanged()
    }
    grid.panel.component.repaint()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}