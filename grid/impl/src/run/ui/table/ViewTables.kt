package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ViewIndex

/** The tables of a [TableResultView], and which table shows each grid column; read-only. [MutableViewTables] changes them. */
internal interface ViewTables {
  val main: GridTable

  /** The pinned table, or null while no visible column is pinned. */
  val pinned: GridTable?

  /** The main table, then the pinned one if there is one. */
  fun all(): List<GridTable>

  /** The table that shows grid column [column]. */
  fun tableOf(column: ViewIndex<GridColumn>): GridTable

  /** The index of grid column [column] in [table]'s slice, or -1 when [table] doesn't show it. */
  fun localIndexOf(table: GridTable, column: ViewIndex<GridColumn>): Int

  /** The view index of the grid column at [localIndex] of [table]'s slice, or an index of -1 when there is none. */
  fun gridColumn(table: GridTable, localIndex: Int): ViewIndex<GridColumn>
}

/**
 * A [ViewTables] with setters: each table is paired with its slice, and answers come from that slice. The view adds and removes the
 * pinned table together with its slice.
 */
internal class MutableViewTables(
  private val grid: DataGrid,
  override val main: GridTable,
  private val mainSlice: GridColumnSlice,
) : ViewTables {
  override var pinned: GridTable? = null
    private set

  private var pinnedSlice: GridColumnSlice? = null

  override fun all(): List<GridTable> = listOfNotNull(main, pinned)

  fun setPinned(table: GridTable, slice: GridColumnSlice) {
    pinned = table
    pinnedSlice = slice
  }

  /** Forgets the pinned table, and gives back its slice, which the view disposes with the table. */
  fun removePinned(): GridColumnSlice? {
    val slice = pinnedSlice
    pinned = null
    pinnedSlice = null
    return slice
  }

  /** The pins changed, so each table's slice picks its columns again. */
  fun pinsChanged() {
    mainSlice.pinsChanged()
    pinnedSlice?.pinsChanged()
  }

  /**
   * The table that shows the column at [position]. The view also asks by position when transposed, where a position is a grid row and
   * nothing is pinned.
   */
  fun tableAt(position: Int): GridTable {
    val pinned = pinned
    return if (pinned != null && localIndexAt(pinned, position) >= 0) pinned else main
  }

  /** The index in [table]'s slice of the column at [position], or -1 when [table] doesn't show it. */
  fun localIndexAt(table: GridTable, position: Int): Int = sliceOf(table)?.toLocal(position) ?: -1

  /** The position of the column at [localIndex] of [table]'s slice, or -1. */
  fun positionAt(table: GridTable, localIndex: Int): Int = sliceOf(table)?.toMaster(localIndex) ?: -1

  override fun tableOf(column: ViewIndex<GridColumn>): GridTable = tableAt(column.asInteger())

  override fun localIndexOf(table: GridTable, column: ViewIndex<GridColumn>): Int = localIndexAt(table, column.asInteger())

  override fun gridColumn(table: GridTable, localIndex: Int): ViewIndex<GridColumn> =
    ViewIndex.forColumn(grid, positionAt(table, localIndex))

  private fun sliceOf(table: GridTable): GridColumnSlice? = when {
    table === main -> mainSlice
    table === pinned -> pinnedSlice
    else -> null
  }
}
