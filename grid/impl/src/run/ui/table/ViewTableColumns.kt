package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridHelper
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.extractors.BinaryDisplayType
import com.intellij.database.run.actions.ChangeColumnDisplayTypeAction.isBinary
import com.intellij.openapi.util.NlsContexts
import javax.swing.Icon

private const val HIERARCHICAL_COLUMN_ADDITIONAL_WIDTH = 40

/** A table column of a grid column, which [ViewColumnCache] creates. It reads its header, icon and tooltip from the grid. */
internal class RegularTableColumn(
  private val grid: DataGrid,
  private val column: GridColumn,
  modelIndex: Int,
) : TableResultViewColumn(modelIndex) {
  private var iconCache: Icon? = null

  override fun getIcon(display: Boolean): Icon? {
    iconCache?.let { return it }
    val icon = GridHelper.get(grid).getColumnIcon(grid, column, display)
    if (display) iconCache = icon
    return icon
  }

  override fun dropModelDependentCache() {
    iconCache = null
  }

  override fun getHeaderValue(): @NlsContexts.ColumnName String {
    val name = grid.getName(column)
    val columnIndex = ModelIndex.forColumn(grid, column.columnNumber)
    if (!isBinary(columnIndex, grid)) return name
    val displayType = grid.getDisplayType(columnIndex)
    val pureDisplayType = grid.getPureDisplayType(columnIndex)
    val displayTypeName =
      if (pureDisplayType == BinaryDisplayType.DETECT && displayType == BinaryDisplayType.HEX) "" else " (" + displayType.name + ")"
    return name + displayTypeName
  }

  override fun getAdditionalWidth(): Int =
    if (column is HierarchicalGridColumn && column.isLeftMostChildOfDirectAncestor) HIERARCHICAL_COLUMN_ADDITIONAL_WIDTH
    else ADDITIONAL_COLUMN_WIDTH

  override fun getMultilineHeaderValues(): @NlsContexts.ColumnName List<String> {
    val result = mutableListOf(getHeaderValue())
    if (column is HierarchicalGridColumn) {
      var wasPreviousNodeLeftMost = column.isLeftMostChildOfDirectAncestor
      var current = column.parent
      while (current != null) {
        result.add(if (wasPreviousNodeLeftMost) current.name else "    ")
        wasPreviousNodeLeftMost = wasPreviousNodeLeftMost and current.isLeftMostChildOfDirectAncestor
        current = current.parent
      }
    }
    return result.reversed()
  }

  override fun getTooltipText(): @NlsContexts.Tooltip String? =
    GridHelper.get(grid).getColumnTooltipHtml(grid, ModelIndex.forColumn(grid, column.columnNumber))
}

/** A table column of a transposed view, which shows a grid row; [ViewColumnCache] creates it. Its header is the row's name. */
internal class TransposedTableColumn(private val grid: DataGrid, modelIndex: Int) : TableResultViewColumn(modelIndex) {
  override fun getIcon(display: Boolean): Icon? = null

  override fun getHeaderValue(): @NlsContexts.ColumnName String = GridUtil.getRowName(grid, modelIndex)

  override fun getMultilineHeaderValues(): @NlsContexts.ColumnName List<String> = listOf(getHeaderValue())

  override fun getTooltipText(): @NlsContexts.Tooltip String? = null
}
