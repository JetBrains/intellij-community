package com.intellij.database.run.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.database.datagrid.color.TableHeatmapColorLayer
import com.intellij.database.datagrid.setHeatmapColoringEnable

class ToggleTableViewColoringModeSequentialAction : ToggleTableViewBaseAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    return getHeatMapColorLayer(e)?.coloringMode == TableHeatmapColorLayer.ColoringMode.SEQUENTIAL
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      getOrCreateHeatMapColorLayer(e)?.coloringMode = TableHeatmapColorLayer.ColoringMode.SEQUENTIAL
      TableHeatmapColorLayer.setColoringMode(TableHeatmapColorLayer.ColoringMode.SEQUENTIAL)
      e.project?.let { setHeatmapColoringEnable(it, true) }
    }
  }
}