package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridModel
import com.intellij.database.datagrid.GridRow
import com.intellij.database.run.ui.grid.GridColorsScheme
import com.intellij.ui.JBAutoScroller
import javax.swing.ListSelectionModel

/**
 * What the view shares with each table it creates: data, row selection, column cache, colors, autoscroll lock, reordering block and
 * view-owned helpers.
 */
internal class SharedTableParts(
  /** The data model the view shows when it creates the table; a transpose replaces it. */
  val dataModel: GridTableModel,
  /** The grid's data with the user's unsubmitted changes, which the tables show. */
  val gridModel: GridModel<GridRow, GridColumn>,
  /** The grid's data as loaded, without unsubmitted changes. */
  val databaseModel: GridModel<GridRow, GridColumn>,
  val rowSelection: ListSelectionModel,
  val columnCache: ViewColumnCache,
  val cellRenderers: ViewCellRenderers,
  val cellEditors: ViewCellEditors,
  /** What the tables' column headers show of the grid's state, which they read as they paint. */
  val headerAppearance: ColumnHeaderAppearance,
  val colorsScheme: GridColorsScheme,
  /** The grid's autoscroll lock, which keeps a selection change from scrolling the tables. */
  val autoscrollLocker: JBAutoScroller.AutoscrollLocker,
  /** The view's block on column reordering, which every table's header reads while a column move is written. */
  val columnReorderingBlock: ColumnReorderingBlock,
)
