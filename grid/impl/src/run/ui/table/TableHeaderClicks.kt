package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridHelper
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ViewIndex
import com.intellij.database.run.actions.ColumnLocalFilterAction
import com.intellij.ui.awt.RelativePoint
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.table.JTableHeader
import javax.swing.table.TableColumnModel

/**
 * What a click on a column header of a [TableResultView] does, in the main table's header and the pinned table's alike. The table says
 * which part of its header the pointer is on; this listener acts on it. A left click selects the column, sorts, opens the local filter
 * or collapses a group. A middle press or release hides the column, and a popup trigger opens the header popup.
 */
internal class TableHeaderClicks(
  private val grid: DataGrid,
  /** The grid's selection model, which the view owns. */
  private val selection: TableSelectionModel,
  /** The view's column model, whose column selection both tables share. */
  private val viewColumns: TableColumnModel,
  /** The view's columns in displayed order, in which a Shift range runs while a column is pinned. */
  private val displayedColumns: DisplayedColumns,
  /** The main table; a header click selects all of its rows, which both tables share. */
  private val mainTable: GridTable,
  /** The view's header popups. */
  private val popups: TableHeaderPopups,
) : MouseAdapter() {
  override fun mouseClicked(event: MouseEvent) = onHeaderEvent(event)

  override fun mousePressed(event: MouseEvent) = onHeaderEvent(event)

  override fun mouseReleased(event: MouseEvent) = onHeaderEvent(event)

  private fun onHeaderEvent(event: MouseEvent) {
    val table = (event.component as JTableHeader).table as GridTable
    when (val hit = table.headerHitAt(event.point)) {
      is ColumnHeaderHit.ColumnCell -> onColumnCellEvent(table, hit.column, hit.part, event)
      is ColumnHeaderHit.RowCell -> onRowCellEvent(hit.row, event)
      ColumnHeaderHit.Outside -> {}
    }
  }

  private fun onColumnCellEvent(table: GridTable, column: ModelIndex<GridColumn>, part: ColumnHeaderPart, event: MouseEvent) {
    if (event.id == MouseEvent.MOUSE_CLICKED && event.button == MouseEvent.BUTTON1) {
      leftClick(column, part, event)
      return
    }
    if (event.id != MouseEvent.MOUSE_CLICKED && event.button == MouseEvent.BUTTON2 && event.clickCount == 1) {
      grid.setColumnEnabled(column, false)
      return
    }
    if (event.isPopupTrigger) {
      if (grid.isHeaderSelecting) {
        val position = column.toView(grid)
        // Right-clicking a column already in the selection keeps it; only a click outside collapses to that column.
        if (!viewColumns.selectionModel.isSelectedIndex(position.asInteger())) selectHeaderColumn(position, event)
      }
      popups.showColumnPopup(column, table, event.component, event.point)
    }
  }

  private fun leftClick(column: ModelIndex<GridColumn>, part: ColumnHeaderPart, event: MouseEvent) {
    when (part) {
      ColumnHeaderPart.Name -> selectHeaderColumn(column.toView(grid), event)
      ColumnHeaderPart.FilterIcon ->
        ColumnLocalFilterAction.createFilterPopup(grid, column)?.show(RelativePoint(event.component, event.point))
      ColumnHeaderPart.SortArrow, ColumnHeaderPart.LeafLine -> sortByClick(column, event)
      is ColumnHeaderPart.GroupLine -> grid.autoscrollLocker.runWithLock {
        GridUtil.collapseColumnsSubtree(grid, column, part.line) { updateColumnHierarchyDepth(grid) }
      }
      ColumnHeaderPart.BlankLine -> {}
    }
  }

  /** A transposed header's cells are grid rows; a press selects one, as a column of the view, and a popup trigger opens the row popup. */
  private fun onRowCellEvent(row: ModelIndex<GridRow>, event: MouseEvent) {
    if (event.id != MouseEvent.MOUSE_PRESSED) return
    if (grid.isHeaderSelecting) selectTransposedHeaderCell(row.toView(grid), event)
    if (event.isPopupTrigger) popups.showRowPopup(event.component, event.point)
  }

  /**
   * Sorts by [column], if the grid can. The click adds it to the sort instead when Alt matches the setting: Alt adds while it is on, a
   * plain click while it is off.
   */
  private fun sortByClick(column: ModelIndex<GridColumn>, event: MouseEvent) {
    if (!GridHelper.get(grid).isSortingApplicable(column)) return
    val alt = event.modifiersEx == InputEvent.ALT_DOWN_MASK
    val settings = GridUtil.getSettings(grid)
    val sortingModel = grid.dataHookup.sortingModel
    val supportsAdditiveSorting = sortingModel == null || !sortingModel.isSortingEnabled || sortingModel.supportsAdditiveSorting()
    toggleSortOrder(grid, column, supportsAdditiveSorting && (settings == null || settings.isAddToSortViaAltClick) == alt)
  }

  /** Selects the whole [column] from its header, or extends or toggles the selection by the modifiers of [event]. */
  private fun selectHeaderColumn(column: ViewIndex<GridColumn>, event: MouseEvent) {
    val interval = GridUtil.isIntervalModifierSet(event)
    val exclusive = GridUtil.isExclusiveModifierSet(event)
    // With a pinned table, a range runs in displayed order, which differs from the view's column model.
    val change = if (exclusive) RangeChange.ADD else RangeChange.REPLACE
    if (interval && displayedColumns.select(viewColumns.selectionModel.anchorSelectionIndex, column.asInteger(), change)) {
      if (exclusive) selection.addRowSelectionInterval(lastRow(), 0) else selection.setRowSelectionInterval(lastRow(), 0)
      return
    }
    selectHeaderPosition(column.asInteger(), interval, exclusive)
  }

  /** Selects the column of a transposed header's cell, which shows [row]; the view's column model holds rows then, and pins nothing. */
  private fun selectTransposedHeaderCell(row: ViewIndex<GridRow>, event: MouseEvent) =
    selectHeaderPosition(row.asInteger(), GridUtil.isIntervalModifierSet(event), GridUtil.isExclusiveModifierSet(event))

  /**
   * Selects every row in the column at [position] of the view's column model, or extends or toggles the selection. The position is an int
   * because it holds a grid column, or a grid row while transposed.
   */
  private fun selectHeaderPosition(position: Int, interval: Boolean, exclusive: Boolean) {
    if (position < 0) return
    val columnSelection = viewColumns.selectionModel
    if (interval) {
      val lead = columnSelection.leadSelectionIndex.takeIf { it in 0 until viewColumns.columnCount } ?: position
      if (exclusive) {
        selection.addRowSelectionInterval(lastRow(), 0)
        selection.addColumnSelectionInterval(position, lead)
      }
      else {
        selection.setRowSelectionInterval(lastRow(), 0)
        selection.setColumnSelectionInterval(position, lead)
      }
    }
    else if (exclusive) {
      if (columnSelection.isSelectedIndex(position)) {
        columnSelection.removeSelectionInterval(position, position)
      }
      else {
        selection.addRowSelectionInterval(lastRow(), 0)
        selection.addColumnSelectionInterval(position, position)
      }
    }
    else {
      selection.setRowSelectionInterval(lastRow(), 0)
      selection.setColumnSelectionInterval(position, position)
    }
  }

  private fun lastRow(): Int = mainTable.rowCount - 1
}
