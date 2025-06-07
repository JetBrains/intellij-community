package com.intellij.database.run.ui

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer


class EditorValueEditorTab : ValueEditorTab {
  override val priority: Int = 20
  override fun createTabInfoProvider(grid: DataGrid, openValueEditorTab: () -> Unit) = ValueTabInfoProvider(grid)
}

/**
 * @author Liudmila Kornilova
 **/
class ValueTabInfoProvider(private val grid: DataGrid) : TabInfoProvider(DataGridBundle.message("EditMaximized.ValueEditor.text"),
                                                                         ActionManager.getInstance().getAction("Console.TableResult.EditMaximized.Value.Group") as? ActionGroup) {
  private var currentViewerFactory: CellViewerFactory
  private val viewerFactories = buildList {
    add(EditorCellViewerFactory)
    add(ReadonlyEditorCellViewerFactory)
    add(EmptyCellViewerFactory)
    addAll(CellViewerFactory.getExternalFactories())
  }
  private var viewer: CellViewer

  init {
    currentViewerFactory = chooseViewerFactory()
    viewer = currentViewerFactory.createViewer(grid)
    updateTabInfo()
  }

  private fun chooseViewerFactory(): CellViewerFactory {
    val rowIdx = grid.selectionModel.leadSelectionRow
    val columnIdx = grid.selectionModel.leadSelectionColumn
    val factory = viewerFactories.maxByOrNull { it.getSuitability(grid, rowIdx, columnIdx) }
    if (factory == null) {
      LOG.error("Cannot find cell viewer factory for $rowIdx $columnIdx")
    }
    return factory ?: EmptyCellViewerFactory
  }

  override fun update(event: UpdateEvent?) {
    updateViewer()
    super.update(event)
  }

  private fun updateViewer() {
    val factory = chooseViewerFactory()
    if (currentViewerFactory != factory) {
      currentViewerFactory = factory
      val oldViewer = viewer
      viewer = factory.createViewer(grid)
      updateTabInfo()
      Disposer.dispose(oldViewer)
    }
  }

  override fun dispose() {
    Disposer.dispose(viewer)
  }

  override fun getViewer(): CellViewer = viewer

  companion object {
    private val LOG = Logger.getInstance(ValueTabInfoProvider::class.java)
  }
}