package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.openapi.actionSystem.DataSink
import java.awt.event.MouseEvent
import java.util.EventObject

/**
 * What a table asks of its view. A click, a key or an edit in one table may change what both tables show. So the table hands it to the
 * view, as an intent, an event, or a question whose answer it follows. There are no getters: a table reads only the models the view
 * installed in it, and its own slice of the view's columns.
 *
 * A cell goes to the view in model indices, which name one cell in both tables. The table converts it with its own index converter.
 */
internal interface GridTableHost {
  /**
   * With a pinned table, commits an editor open in either table unless it edits the target cell at [row] and [column]. False when an
   * editor refuses; the selection then stays. A target outside the table has index -1, which matches no editor, so every open editor
   * commits.
   */
  fun stopEditingBeforeSelectionChange(row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>): Boolean
  /**
   * Whether the table goes on with [event]. A non-left press, or a Ctrl+click on macOS, first commits an open editor and is dropped if it
   * refuses. A listener would be too late: JBTable's own runs first, moves the row selection on a right press and ignores consumed events.
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
  /** The table is about to paint, so the view sizes its cell image cache to what all its tables show now. */
  fun cellImageCacheSizeChanged()
  /** Adds the grid's action state, and where an open header popup for this table opened. */
  fun uiDataSnapshot(sink: DataSink)
  /** Writes a value committed by a cell editor of the table, to that cell or to every selected cell. */
  fun setValueAt(value: Any?, row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>, allowImmediateUpdate: Boolean,
                 source: GridRequestSource, metadata: Any?)
  /** Commits an editor open in the view's other table. False when it refuses, and no edit starts in this table. */
  fun stopEditingBeforeEdit(): Boolean
  /**
   * Whether the view takes the edit of the cell at [row] and [column]; [startingEvent] starts it, and is null for an action with no event.
   * When true, the view opens the value editor for a double click, a typed key, or any other start. The table then opens no cell editor.
   * The table has to ask: Swing's input handler and the edit actions start an edit by calling `editCellAt`, which no listener can veto.
   */
  fun editsInValueEditor(row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>, startingEvent: EventObject?): Boolean
}
