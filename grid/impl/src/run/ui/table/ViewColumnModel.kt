package com.intellij.database.run.ui.table

import javax.swing.event.TableColumnModelEvent
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.TableColumn

/**
 * The view's column model: its visible columns in grid order, pinned ones too, and their selection. Each table shows its columns through a
 * [GridColumnSlice]: the main table the unpinned ones, the pinned table the pinned ones. A position in this model is a grid column's
 * view index, or a grid row's in a transposed view.
 */
internal class ViewColumnModel : DefaultTableColumnModel() {
  override fun addColumn(aColumn: TableColumn?) {
    require(aColumn is TableResultViewColumn) { "Unexpected column type" }
    super.addColumn(aColumn)
  }

  override fun getColumn(columnIndex: Int): TableResultViewColumn = super.getColumn(columnIndex) as TableResultViewColumn

  /** Removes every column, with one change of the column selection rather than one per column. Each removal still fires its own event. */
  fun removeAllColumns() {
    val columnCount = tableColumns.size
    if (columnCount == 0) return

    selectionModel?.removeIndexInterval(0, columnCount - 1)
    for (column in tableColumns) {
      column.removePropertyChangeListener(this)
    }
    totalColumnWidth = -1 // javax.swing.table.DefaultTableColumnModel.invalidateWidthCache
    for (i in columnCount - 1 downTo 0) {
      tableColumns.removeAt(i)
      fireColumnRemoved(TableColumnModelEvent(this, i, 0))
    }
  }
}
