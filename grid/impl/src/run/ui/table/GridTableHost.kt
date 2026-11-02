package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent

/**
 * What a table asks of its view. A click, a key or an edit in one table may change what both tables show. So the table hands it to the
 * view, as an intent, an event, or a question whose answer it follows. There are no getters: a table reads only the models the view
 * installed in it, and its own slice of the view's columns.
 *
 * A `row` is a row of the table; both tables share the row order, so it is the same in either. A `localIndex` is an index in this table's
 * slice, as Swing gives it. It matches the grid column only in the main table while nothing is pinned, so the view maps it.
 */
internal interface GridTableHost {
  /**
   * With a pinned table, commits an editor open in either table unless it edits the target cell. False when an editor refuses; the
   * selection then stays.
   */
  fun stopEditingBeforeSelectionChange(row: Int, localIndex: Int): Boolean
  /** Whether the view selected the cell range in displayed order; false, without a pinned table, leaves the range to Swing. */
  fun selectCellRange(row: Int, localIndex: Int, addToSelection: Boolean): Boolean
  /**
   * Whether the table goes on with [event]: a non-left press first commits an open editor, and is dropped if the editor refuses. A listener
   * can't drop a press, because AWT calls every listener.
   */
  fun beforeMouseEvent(event: MouseEvent): Boolean
  /**
   * The table handled [event]; a left drag may carry the selection across the divider. A listener would miss it: during such a drag,
   * Swing's autoscroller passes real drags to no listener. The pinned table keeps Swing's autoscroller: `JBAutoScroller`, which turns it
   * off, is installed only on the main table, its header and the row header.
   */
  fun processMouseMotionEvent(event: MouseEvent)
  /** The width [column] gets for [requestedWidth]. A pinned column may shrink freely, but grows only while the pinned columns still fit. */
  fun constrainColumnResize(column: TableResultViewColumn, requestedWidth: Int): Int
  /** A column of the table changed width, so the view lays out the pinned table's viewport again. */
  fun columnMarginChanged()
  /** Stores a height for [row] of the table in the view's row heights, which every table of the view then applies. */
  fun setRowHeight(row: Int, height: Int)
  /** A click on a column header of the table sorts the grid by that column. */
  fun toggleSortOrder(column: ModelIndex<GridColumn>, additive: Boolean)
  /** The table is about to paint, so the view sizes its cell image cache to what all its tables show now. */
  fun cellImageCacheSizeChanged()
  /** Adds the grid's action state for actions that run in the table. */
  fun uiDataSnapshot(sink: DataSink)
  /** Writes a value committed by a cell editor of the table, to that cell or to every selected cell. */
  fun setValueAt(value: Any?, row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>, allowImmediateUpdate: Boolean,
                 source: GridRequestSource, metadata: Any?)
  /** Commits an editor open in the view's other table. False when it refuses, and no edit starts in this table. */
  fun stopEditingBeforeEdit(): Boolean
  /** A header popup about [column] opens, so the view's actions work on that column. */
  fun setContextColumn(column: ModelIndex<GridColumn>)
  /**
   * The header popup about [column] closed. Returns true when the view forgot [column], and false when a popup about another column has
   * taken its place since.
   */
  fun columnPopupClosed(column: ModelIndex<GridColumn>): Boolean
  /**
   * A click on the header of the column at [localIndex]; the view selects that column, or extends or toggles the selection by the
   * modifiers.
   */
  fun selectHeaderColumn(localIndex: Int, event: MouseEvent)
  /** A middle click on the header of [column] in the table. */
  fun hideColumn(column: ModelIndex<GridColumn>)
  /** A click on a group's line in the header of [column]; [headerLine] counts the header's lines from the top, not a grid index. */
  fun collapseColumnGroup(column: ModelIndex<GridColumn>, headerLine: Int)
  /** A click on the filter icon in the header of [column], at [location]. */
  fun showLocalFilter(column: ModelIndex<GridColumn>, location: RelativePoint)
  /** A cell of the table asks for the value editor. */
  fun openValueEditor()
}
