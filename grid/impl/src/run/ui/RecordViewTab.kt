package com.intellij.database.run.ui

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.util.Disposer

class RecordViewTab : ValueEditorTab {
  override val priority: Int
    get() = 25

  override fun createTabInfoProvider(grid: DataGrid, openValueEditorTab: () -> Unit): TabInfoProvider {
    return RecordViewInfoProvider(grid, openValueEditorTab)
  }
}

class RecordViewInfoProvider(grid: DataGrid, openValueEditorTab: () -> Unit) : TabInfoProvider(
  DataGridBundle.message("EditMaximized.Record.text"),
  ActionManager.getInstance().getAction("Console.TableResult.EditMaximized.Record.Group") as? ActionGroup
) {
  private val viewer = RecordView(grid, openValueEditorTab)

  init {
    updateTabInfo()
  }

  override fun update(event: UpdateEvent?) {
    isUpdated = false
    if (isOnTab) {
      super.update(event)
      if (!viewer.validateIfNeeded()) {
        updateTabInfo()
      }
    }
  }

  override fun getViewer(): RecordView {
    return viewer
  }

  override fun dispose() {
    Disposer.dispose(viewer)
  }

}