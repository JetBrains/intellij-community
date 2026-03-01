package com.intellij.database.run.actions

import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.color.GridColorModelImpl
import com.intellij.database.datagrid.color.TableHeatmapColorLayer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

abstract class ToggleTableViewBaseAction : DumbAwareToggleAction() {
  override fun update(e: AnActionEvent) {
    val dataGrid = GridUtil.getDataGrid(e.dataContext)
    e.presentation.isEnabled = dataGrid != null
    if (e.presentation.isEnabled) {
      super.update(e)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  protected fun getOrCreateHeatMapColorLayer(e: AnActionEvent) : TableHeatmapColorLayer? {
    val heatmapColorLayer = getHeatMapColorLayer(e)
    if (heatmapColorLayer != null)
      return heatmapColorLayer

    val grid = GridUtil.getDataGrid(e.dataContext) ?: return null
    return TableHeatmapColorLayer.installOn(grid)
  }

  protected fun getHeatMapColorLayer(e: AnActionEvent): TableHeatmapColorLayer? {
    return getGridColorModel(e)?.getLayer(TableHeatmapColorLayer::class.java) as? TableHeatmapColorLayer
  }

  private fun getGridColorModel(e: AnActionEvent): GridColorModelImpl? {
    val grid = GridUtil.getDataGrid(e.dataContext) ?: return null
    return grid.colorModel as? GridColorModelImpl
  }
}