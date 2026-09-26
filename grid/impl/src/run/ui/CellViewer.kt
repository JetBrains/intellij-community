package com.intellij.database.run.ui

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridCellRequest
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Key
import javax.swing.JComponent

/**
 * Content component shown by an Edit Maximized tab for the current grid selection.
 *
 * Separate from the grid's inline cell editing path, which uses `GridCellEditor`.
 * The historical "viewer" name does not imply read-only behavior: implementations may be editable
 * or read-only, and different [CellViewerFactory] implementations can provide specialized content
 * such as text editors, image viewers, or array views.
 */
interface CellViewer : Disposable {
  val component: JComponent
  val preferedFocusComponent: JComponent?
  val toolbarTargetComponent: JComponent
    get() = component
  fun update(event: UpdateEvent? = null)

  companion object {
    @JvmField
    val CELL_VIEWER_KEY: Key<CellViewer> = Key("CELL_VIEWER_KEY")
  }
}

/**
 * Chooses and creates the Value Editor [CellViewer] implementation for the current cell.
 */
interface CellViewerFactory {
  fun getSuitability(request: GridCellRequest<GridRow, GridColumn>): Suitability
  fun createViewer(grid: DataGrid): CellViewer

  companion object {
    private val EP_NAME = ExtensionPointName<CellViewerFactory>("com.intellij.database.datagrid.cellViewerFactory")
    fun getExternalFactories(): List<CellViewerFactory> = EP_NAME.extensionList
  }
}

sealed interface UpdateEvent {
  data object ContentChanged : UpdateEvent
  data object SelectionChanged : UpdateEvent
  data object SettingsChanged : UpdateEvent
  data class ValueChanged(val value: Any?) : UpdateEvent
}

enum class Suitability {
  NONE,
  MIN_1,
  MIN_2,
  MAX
}