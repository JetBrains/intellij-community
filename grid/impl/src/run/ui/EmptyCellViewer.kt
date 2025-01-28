package com.intellij.database.run.ui

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.ui.components.JBPanelWithEmptyText
import javax.swing.JComponent

/**
 * @author Liudmila Kornilova
 **/
class EmptyCellViewer : CellViewer {
  private val panel = JBPanelWithEmptyText()

  init {
    panel.withEmptyText(DataGridBundle.message("no.cell.selected"))
  }

  override val component: JComponent
    get() = panel
  override val preferedFocusComponent: JComponent
    get() = panel

  override fun update(event: UpdateEvent?) = Unit
  override fun dispose() = Unit
}

object EmptyCellViewerFactory : CellViewerFactory {
  override fun getSuitability(grid: DataGrid, row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>): Suitability {
    return if (!row.isValid(grid) || !column.isValid(grid)) Suitability.MAX else Suitability.NONE
  }

  override fun createViewer(grid: DataGrid): CellViewer {
    return EmptyCellViewer()
  }
}
