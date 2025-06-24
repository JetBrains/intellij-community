package com.intellij.database.run.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.database.datagrid.color.TableHeatmapColorLayer
import com.intellij.database.datagrid.setHeatmapColoringEnable

class ToggleTableViewColoringModeDivergingAction : ToggleTableViewBaseAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    return getHeatMapColorLayer(e)?.coloringMode == TableHeatmapColorLayer.ColoringMode.DIVERGING
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      getOrCreateHeatMapColorLayer(e)?.coloringMode = TableHeatmapColorLayer.ColoringMode.DIVERGING
      TableHeatmapColorLayer.setColoringMode(TableHeatmapColorLayer.ColoringMode.DIVERGING)
      e.project?.let { setHeatmapColoringEnable(it, true) }
    }
  }
}