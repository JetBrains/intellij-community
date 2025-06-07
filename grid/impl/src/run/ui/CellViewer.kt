package com.intellij.database.run.ui

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Key
import javax.swing.JComponent

/**
 * @author Liudmila Kornilova
 **/
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

interface CellViewerFactory {
  fun getSuitability(grid: DataGrid, row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>): Suitability
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