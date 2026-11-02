package com.intellij.database.run.ui.table

import javax.swing.event.TableColumnModelEvent
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.TableColumn

/**
 * The view's column model: its visible columns in grid order, pinned ones too, and their selection. Each table shows its columns through a
 * [GridColumnSlice]: the main table the unpinned ones, the pinned table the pinned ones. A position in this model is a grid column's
 * view index, or a grid row's in a transposed view.
 *
 * Swing changes the selection in the middle of a removal or a move, before the tables follow. So the model tells its
 * [ColumnModelChangeListener]s when each change starts and ends.
 */
internal class ViewColumnModel : DefaultTableColumnModel() {
  private val changeListeners = ArrayList<ColumnModelChangeListener>()

  fun addChangeListener(listener: ColumnModelChangeListener) {
    changeListeners.add(listener)
  }

  override fun addColumn(aColumn: TableColumn?) {
    require(aColumn is TableResultViewColumn) { "Unexpected column type" }
    fireChangeStarted()
    try {
      super.addColumn(aColumn)
    }
    finally {
      fireChangeFinished()
    }
  }

  override fun removeColumn(column: TableColumn?) {
    fireChangeStarted()
    try {
      super.removeColumn(column)
    }
    finally {
      fireChangeFinished()
    }
  }

  override fun moveColumn(columnIndex: Int, newIndex: Int) {
    fireChangeStarted()
    try {
      super.moveColumn(columnIndex, newIndex)
    }
    finally {
      fireChangeFinished()
    }
  }

  override fun getColumn(columnIndex: Int): TableResultViewColumn = super.getColumn(columnIndex) as TableResultViewColumn

  /** Removes every column, with one change of the column selection rather than one per column. Each removal still fires its own event. */
  fun removeAllColumns() {
    val columnCount = tableColumns.size
    if (columnCount == 0) return

    fireChangeStarted()
    try {
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
    finally {
      fireChangeFinished()
    }
  }

  private fun fireChangeStarted() {
    for (listener in changeListeners) {
      listener.changeStarted()
    }
  }

  private fun fireChangeFinished() {
    for (listener in changeListeners) {
      listener.changeFinished()
    }
  }
}
