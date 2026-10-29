package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ViewIndex

/**
 * The tables of a [TableResultView] as the pinned-table controller and the row order see them. They read which table shows a grid column;
 * only the view changes it, through [ViewTables].
 */
internal interface ViewTableLookup {
  val main: GridTable

  /** The pinned table, or null while no visible column is pinned. */
  val pinned: GridTable?

  /** The main table, then the pinned one if there is one. */
  fun all(): List<GridTable>

  /** The table that shows grid column [column]. */
  fun tableOf(column: ViewIndex<GridColumn>): GridTable

  /** The index of grid column [column] in [table], or -1 when [table] doesn't show it. */
  fun localColumn(table: GridTable, column: ViewIndex<GridColumn>): Int

  /** The grid column at index [localColumn] of [table]; not valid when there is none. */
  fun gridColumn(table: GridTable, localColumn: Int): ViewIndex<GridColumn>
}

/**
 * The tables of a [TableResultView] and the columns each one shows: the main table, and the pinned table while a visible column is
 * pinned. The view adds and drops the pinned table here; others read it as a [ViewTableLookup], so one place tells which table shows
 * a column.
 *
 * A position is a position in the view's column model, as Swing gives it. Untransposed, it is a grid column's view index, which the
 * [ViewIndex] methods take. Transposed, it is a grid row, which only the view asks about, since nothing is pinned then.
 */
internal class ViewTables(
  private val grid: DataGrid,
  override val main: GridTable,
  private val mainColumns: GridColumnSlice,
) : ViewTableLookup {
  override var pinned: GridTable? = null
    private set

  private var pinnedColumns: GridColumnSlice? = null

  override fun all(): List<GridTable> = listOfNotNull(main, pinned)

  fun pinnedTableAdded(table: GridTable, columns: GridColumnSlice) {
    pinned = table
    pinnedColumns = columns
  }

  /** Forgets the pinned table, and gives back its slice, which the view disposes with the table. */
  fun pinnedTableDropped(): GridColumnSlice? {
    val columns = pinnedColumns
    pinned = null
    pinnedColumns = null
    return columns
  }

  /** The pins changed, so each table's slice picks its columns again. */
  fun pinsChanged() {
    mainColumns.pinsChanged()
    pinnedColumns?.pinsChanged()
  }

  /** The table that shows the column at [position]. */
  fun tableAt(position: Int): GridTable {
    val pinned = pinned
    return if (pinned != null && localIndex(pinned, position) >= 0) pinned else main
  }

  /** The index in [table] of the column at [position], or -1 when [table] doesn't show it. */
  fun localIndex(table: GridTable, position: Int): Int = columnsOf(table)?.toLocal(position) ?: -1

  /** The position of the column at [localIndex] of [table], or -1. */
  fun position(table: GridTable, localIndex: Int): Int = columnsOf(table)?.toMaster(localIndex) ?: -1

  override fun tableOf(column: ViewIndex<GridColumn>): GridTable = tableAt(column.asInteger())

  override fun localColumn(table: GridTable, column: ViewIndex<GridColumn>): Int = localIndex(table, column.asInteger())

  override fun gridColumn(table: GridTable, localColumn: Int): ViewIndex<GridColumn> =
    ViewIndex.forColumn(grid, position(table, localColumn))

  private fun columnsOf(table: GridTable): GridColumnSlice? = when {
    table === main -> mainColumns
    table === pinned -> pinnedColumns
    else -> null
  }
}
