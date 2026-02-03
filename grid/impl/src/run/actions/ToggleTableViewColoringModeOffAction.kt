package com.intellij.database.run.actions

import com.intellij.database.datagrid.color.TableHeatmapColorLayer
import com.intellij.database.datagrid.setHeatmapColoringEnable
import com.intellij.openapi.actionSystem.AnActionEvent

/** Enables or disables heatmap-styled coloring of table cells. */
class ToggleTableViewColoringModeOffAction : ToggleTableViewBaseAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    val heatmapLayer = getHeatMapColorLayer(e) ?: return true
    return heatmapLayer.coloringMode == TableHeatmapColorLayer.ColoringMode.OFF
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      getHeatMapColorLayer(e)?.coloringMode = TableHeatmapColorLayer.ColoringMode.OFF
      TableHeatmapColorLayer.setColoringMode(TableHeatmapColorLayer.ColoringMode.OFF)
      e.project?.let { setHeatmapColoringEnable(it, false) }
    }
  }
}