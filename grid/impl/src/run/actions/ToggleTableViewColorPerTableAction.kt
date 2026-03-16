package com.intellij.database.run.actions

import com.intellij.database.datagrid.color.TableHeatmapColorLayer
import com.intellij.openapi.actionSystem.AnActionEvent

class ToggleTableViewColorPerTableAction : ToggleTableViewBaseAction() {

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (e.presentation.isEnabled) {
      e.presentation.isEnabled = getHeatMapColorLayer(e) != null
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val heatmapLayer = getHeatMapColorLayer(e) ?: return false
    return !heatmapLayer.perColumn
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val heatmapLayer = getHeatMapColorLayer(e) ?: return
    heatmapLayer.perColumn = !state

    TableHeatmapColorLayer.setPerColumnColoringEnabled(heatmapLayer.perColumn)
  }
}