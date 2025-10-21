package com.intellij.database.run.actions

import com.intellij.database.DataGridBundle
import com.intellij.database.DatabaseDataKeys
import com.intellij.database.datagrid.GridHelper
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.run.ui.table.TableResultView
import com.intellij.database.run.ui.table.statisticsPanel.StatisticsPanelMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification.Frontend
import com.intellij.openapi.project.DumbAware

abstract class StatisticsShowAction(private val presentationMode: StatisticsPanelMode) : ToggleAction(), DumbAware, Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val dataGrid = GridUtil.getDataGrid(e.dataContext)
    e.presentation.isEnabledAndVisible = GridHelper.supportsTableStatistics(dataGrid)
    super.update(e)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY) ?: return false
    val tableWithStatistics = grid.resultView as? TableResultView ?: return false

    return tableWithStatistics.getStatisticsPanelMode() == presentationMode
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY) ?: return
    val tableWithStatistics = grid.resultView as? TableResultView ?: return

    tableWithStatistics.setStatisticsPanelMode(presentationMode)
  }

  class StatisticsShowOff : StatisticsShowAction(StatisticsPanelMode.OFF)

  class StatisticsShowCompact : StatisticsShowAction(StatisticsPanelMode.COMPACT)

  class StatisticsShowDetailed : StatisticsShowAction(StatisticsPanelMode.DETAILED)
}

/**
 * This class is needed to make the popup close after choosing an option.
 * Because all actions in the group are toggle actions, then this group is considered multi-closable.
 */
class StatisticsShowActionGroup : DefaultActionGroup(), DumbAware, Frontend {
  init {
    addSeparator(DataGridBundle.message("action.Console.StatisticsShow.separator"))
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    // Hide the icon of this group if there is no statistics
    val dataGrid = GridUtil.getDataGrid(e.dataContext)
    e.presentation.isEnabledAndVisible = GridHelper.supportsTableStatistics(dataGrid)
  }
}