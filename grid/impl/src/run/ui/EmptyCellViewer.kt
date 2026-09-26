package com.intellij.database.run.ui

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridCellRequest
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
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
  override fun getSuitability(request: GridCellRequest<GridRow, GridColumn>): Suitability {
    return if (!request.isValid()) Suitability.MAX else Suitability.NONE
  }

  override fun createViewer(grid: DataGrid): CellViewer {
    return EmptyCellViewer()
  }
}
