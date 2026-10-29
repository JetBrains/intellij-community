package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.openapi.actionSystem.DataSink
import java.awt.event.MouseEvent
import javax.swing.ActionMap

/**
 * Requests that a local table sends to the complete view. Only intents. A table reads only the models the view gave it when it installed
 * the table, and its own slice of the view's column model. On a transpose, the view's row order gives it a new sorter. The table never asks
 * the view for the view's column model: the grid's columns and their order are the view's.
 *
 * A `row` is a row of the table, the same in both tables, which share the row order. A `localColumn` is a column of this table, as Swing
 * gives it. In a pinned table that is not the grid's column index, so the view maps it.
 */
internal interface GridTableHost {
  fun stopEditingBeforeSelectionChange(row: Int, localColumn: Int): Boolean
  fun handleCellRangeSelection(row: Int, localColumn: Int, addToSelection: Boolean): Boolean
  /** Whether the table goes on with [event]: a non-left press first commits an open editor, and is dropped if the editor refuses. */
  fun beforeMouseEvent(event: MouseEvent): Boolean
  /** The table handled [event]; a left press starts a drag that may go on across the divider. */
  fun afterMouseEvent(event: MouseEvent)
  fun processMouseMotionEvent(event: MouseEvent)
  fun installColumnNavigationActions(actionMap: ActionMap)
  fun constrainColumnResize(column: TableResultViewColumn, width: Int): Int
  fun columnMarginChanged()
  /** Stores a height for [row] of the table in the view's row heights, which every table of the view then applies. */
  fun setRowHeight(row: Int, height: Int)
  fun changeFontSize(increment: Int, scale: Double)
  /** A click on a column header of the table sorts the grid by that column. */
  fun toggleSortOrder(column: ModelIndex<GridColumn>, additive: Boolean)
  /** The table shows a different number of cells, so the view resizes its cache for all its tables. */
  fun cellImageCacheSizeChanged()
  /** Adds the grid's data for actions that run in the table. */
  fun uiDataSnapshot(sink: DataSink)
  /** Writes a value committed by a cell editor of the table, to that cell or to every selected cell. */
  fun setValueAt(value: Any?, row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>, allowImmediateUpdate: Boolean,
                 source: GridRequestSource, metadata: Any?)
  fun stopEditingBeforeEdit(): Boolean
  /** A header popup about [column] opens, so the view's actions work on that column. */
  fun setContextColumn(column: ModelIndex<GridColumn>)
  /**
   * The header popup about [column] closed. The view forgets the column, unless a popup about another column has taken its place
   * since, and tells whether it did.
   */
  fun columnPopupClosed(column: ModelIndex<GridColumn>): Boolean
  fun selectHeaderColumn(localColumn: Int, event: MouseEvent)
}
