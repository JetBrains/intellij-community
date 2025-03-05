package com.intellij.database.run.ui

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.util.Disposer

class AggregateValueEditorTab : ValueEditorTab {
  override val priority: Int = 10
  override fun createTabInfoProvider(grid: DataGrid, openValueEditorTab: () -> Unit) = AggregatesTabInfoProvider(grid)
}

/**
 * @author Liudmila Kornilova
 **/
class AggregatesTabInfoProvider(grid: DataGrid) : TabInfoProvider(
  DataGridBundle.message("EditMaximized.Aggregates.text"),
  ActionManager.getInstance().getAction("Console.TableResult.EditMaximized.Aggregates.Group") as? ActionGroup
) {
  private val viewer = AggregateView(grid)

  init {
    updateTabInfo()
  }

  override fun getViewer(): CellViewer = viewer

  override fun dispose() {
    Disposer.dispose(viewer)
  }
}