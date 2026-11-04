package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex

/**
 * What a point of a table's column header falls on. The table answers from the layout of its header; the view decides what a click there
 * does.
 */
internal sealed interface ColumnHeaderHit {
  /** No cell of the grid: past the last column. */
  data object Outside : ColumnHeaderHit

  /** A cell of a transposed header, which shows [row] of the grid. */
  data class RowCell(val row: ModelIndex<GridRow>) : ColumnHeaderHit

  /** The header cell of [column]; [part] says which part of it. */
  data class ColumnCell(val column: ModelIndex<GridColumn>, val part: ColumnHeaderPart) : ColumnHeaderHit
}

/** A part of a column's header cell. */
internal sealed interface ColumnHeaderPart {
  /** A plain column's header, away from its filter icon and sort arrow. */
  data object Name : ColumnHeaderPart

  /** The filter icon of a plain column. */
  data object FilterIcon : ColumnHeaderPart

  /** The sort arrow of a plain column. */
  data object SortArrow : ColumnHeaderPart

  /** The column's own line in a hierarchical header. */
  data object LeafLine : ColumnHeaderPart

  /** The line of a group the column is in, in a hierarchical header; [line] counts the header's lines from the top, not a grid index. */
  data class GroupLine(val line: Int) : ColumnHeaderPart

  /** A line of a hierarchical header with no name on it. */
  data object BlankLine : ColumnHeaderPart
}
