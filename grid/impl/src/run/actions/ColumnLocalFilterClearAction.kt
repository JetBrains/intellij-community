package com.intellij.database.run.actions

import com.intellij.database.DatabaseDataKeys
import com.intellij.database.run.ui.table.TableResultView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction

class ColumnLocalFilterClearAllAction : DumbAwareAction(), GridAction, ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY)
    e.presentation.isEnabledAndVisible = grid != null && grid.resultView is TableResultView
  }


  override fun actionPerformed(e: AnActionEvent) {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY) ?: return
    val resultView = grid.resultView as? TableResultView ?: return

    resultView.localFilterState.reset()
    resultView.localFilterState.isEnabled = true
    resultView.updateRowFilter()
    grid.panel.component.repaint()
  }
}
