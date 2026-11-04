package com.intellij.database.run.ui.table

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
   * With a pinned table, commits an editor open in either table unless it edits [target]. False when an editor refuses; the selection
   * then stays. A [target] outside the table is null, so every open editor commits.
   */
  fun stopEditingBeforeSelectionChange(target: ModelCell?): Boolean
  /**
   * Whether the table goes on with [event]. A non-left press, or a Ctrl+click on macOS, first commits an open editor and is dropped if it
   * refuses. A listener would be too late: JBTable's own runs first, moves the row selection on a right press and ignores consumed events.
   */
  fun beforeMouseEvent(event: MouseEvent): Boolean
  /**
   * The table handled [event]; a left drag may carry the selection across the divider. A listener would miss it: during such a drag,
   * Swing's autoscroller passes real drags to no listener. The pinned table keeps Swing's autoscroller: `JBAutoScroller`, which turns it
   * off, is installed only on the main table and its header.
   */
  fun processMouseMotionEvent(event: MouseEvent)
  /** The width [column] gets for [requestedWidth]. A pinned column may shrink freely, but grows only while the pinned columns still fit. */
  fun constrainColumnResize(column: TableResultViewColumn, requestedWidth: Int): Int
  /** A column of the table changed width, so the view lays out the pinned table's viewport again. */
  fun columnMarginChanged()
  /** The table is about to paint, so the view sizes its cell image cache to what all its tables show now. */
  fun aboutToPaint()
  /** Adds the grid's action state, and where an open header popup for this table opened. */
  fun uiDataSnapshot(sink: DataSink)
  /** Writes [value], which a cell editor of the table committed, to [cell], or to every selected cell. */
  fun commitEditedValue(value: Any?, cell: ModelCell)
  /**
   * The table starts an edit of [cell], null outside the table; [startingEvent] starts it, and is null for an action with no event. The
   * view commits an editor open in its other table, then decides who edits the cell, and the table follows the answer. The table has to
   * ask: Swing's input handler and the edit actions start an edit by calling `editCellAt`, which no listener can veto.
   */
  fun startEdit(cell: ModelCell?, startingEvent: EventObject?): EditStart
}

/** Who edits a cell whose edit a table starts, as its view decides. */
internal enum class EditStart {
  /** An editor open in the view's other table refused to commit, so no edit starts. */
  REFUSED,
  /** The cell edits in the value editor, which the view opens for a double click, a typed key, or any other start. */
  IN_VALUE_EDITOR,
  /** The table opens its cell editor. */
  IN_CELL_EDITOR,
}
