package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ResultViewColumn
import com.intellij.database.run.ui.ColumnMove
import com.intellij.database.run.ui.grid.GridRowHeader
import com.intellij.openapi.util.Ref
import java.awt.event.MouseEvent
import javax.swing.ActionMap

/** Requests that a local table sends to the complete view. */
internal interface GridTableHost {
  fun isPrimary(): Boolean
  fun applyColumnsDisplayOrder(): Boolean
  fun columnMoved(move: ColumnMove)
  fun createRowHeader(): GridRowHeader
  fun stopEditingBeforeSelectionChange(row: Int, column: Int): Boolean
  fun handleCellRangeSelection(row: Int, column: Int, toggle: Boolean): Boolean
  fun afterChangeSelection(column: Int, toggle: Boolean, extend: Boolean)
  fun processMouseEvent(event: MouseEvent, localHandler: Runnable)
  fun processMouseMotionEvent(event: MouseEvent)
  fun installColumnNavigationActions(actionMap: ActionMap)
  fun adjustColumnMoveTarget(target: Int): Int
  fun constrainFrozenColumnResize(column: TableResultViewColumn, width: Int): Int
  fun columnMarginChanged()
  fun syncRowHeight(row: Int, height: Int)
  fun changeFontSize(increment: Int, scale: Double)
  /** Writes a value committed by a cell editor of the table, to that cell or to every selected cell. */
  fun setValueAt(value: Any?, row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>, allowImmediateUpdate: Boolean,
                 source: GridRequestSource, metadata: Any?)
  fun constrainSelectedColumnWidthDelta(columns: IntArray, delta: Int): Int
  fun renderedColumnAt(column: Int): ResultViewColumn?
  fun toGridViewColumn(column: Int): Int
  fun stopEditingBeforeEdit(): Boolean
  fun getCommonValue(): Ref<Any?>?
  fun clearCommonValue()
  fun getContextColumn(): ModelIndex<GridColumn>
  fun setContextColumn(column: ModelIndex<GridColumn>)
  fun isColumnSelected(column: Int): Boolean
  fun selectHeaderColumn(column: Int, event: MouseEvent)
  fun getSelectedRows(): IntArray
  fun getSelectedColumns(): IntArray
  fun columnToModel(column: Int): Int
  fun rowToModel(row: Int): Int
  fun getAvailableColumnsWidth(): Int
  fun getColumnForPersistence(column: ModelIndex<*>): ResultViewColumn?
}
