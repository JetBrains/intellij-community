package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.isArrayCell
import com.intellij.database.datagrid.request
import com.intellij.database.remote.jdbc.LobInfo
import com.intellij.database.run.ui.DataAccessType
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.UIUtil
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.EventObject

/**
 * Which cells the view edits in the value editor instead of a cell editor, and which input opens it. These are a fully loaded LOB, and an
 * array cell while arrays have their own editor.
 */
internal class ValueEditorCells(private val grid: DataGrid) {
  /** Whether the value editor, not a cell editor, edits the cell at [row] and [column]. */
  fun isValueEditorCell(row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>): Boolean {
    val value = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getValueAt(row, column)
    return value is LobInfo.ClobInfo && value.isFullyReloaded ||
           value is LobInfo.BlobInfo && value.isFullyReloaded ||
           isArrayEditorCell(row, column)
  }

  /** Whether [startingEvent] opens the value editor: a double click, a typed key, or any other start, such as an action with no event. */
  fun opensValueEditor(startingEvent: EventObject?): Boolean = when (startingEvent) {
    is MouseEvent -> startingEvent.clickCount >= 2
    is KeyEvent -> UIUtil.isReallyTypedEvent(startingEvent)
    else -> true
  }

  private fun isArrayEditorCell(row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>): Boolean {
    val settings = GridUtil.getSettings(grid)
    return Registry.`is`("database.new.arrays.editor", false) && settings != null && !settings.isEditArrayAsText &&
           isArrayCell(grid.request(row, column))
  }
}
