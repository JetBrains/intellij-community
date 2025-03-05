package com.intellij.database.datagrid

import com.intellij.database.DatabaseDataKeys
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.util.containers.TreeTraversal
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JTable
import javax.swing.SwingUtilities

fun findAllGridsInFile(grid: DataGrid): List<DataGrid> {
  val fileEditor = getOuterFileEditor(grid)
  val editorComponent = fileEditor?.component ?: return emptyList()
  return UIUtil.uiTraverser(editorComponent)
    .expand { component: Component? ->
      DataManager.getInstance().getDataContext(component).getData(DatabaseDataKeys.DATA_GRID_KEY) == null
    }
    .traverse(TreeTraversal.PRE_ORDER_DFS)
    .filterMap { component: Component? ->
      val context = DataManager.getInstance().getDataContext(component)
      context.getData(DatabaseDataKeys.DATA_GRID_KEY)
    }.toList()
}

@Deprecated("Do not please do not create data contexts")
private fun getOuterFileEditor(grid: DataGrid?): FileEditor? {
  if (grid == null) return null
  val gridParentComponent = grid.panel.component.parent
  val dataContext = DataManager.getInstance().getDataContext(gridParentComponent)
  return dataContext.getData(PlatformCoreDataKeys.FILE_EDITOR)
}

fun setPageSize(hookUp: GridDataHookUp<GridRow, GridColumn>, helper: GridHelper) {
  hookUp.pageModel.pageSize = if (helper.isLimitDefaultPageSize) helper.defaultPageSize else GridPagingModel.UNLIMITED_PAGE_SIZE
}

fun setupDynamicRowHeight(table: JTable) {
  table.model.addTableModelListener {
    SwingUtilities.invokeLater { updateRowHeights(table) }
  }
}

private fun updateRowHeights(table: JTable) {
  var row = 0
  val rc = table.rowCount
  while (row < rc) {
    val height = preferredRowHeight(table, row)
    table.setRowHeight(row, height)
    ++row
  }
}

private fun preferredRowHeight(table: JTable, row: Int): Int {
  var height = table.rowHeight
  var col = 0
  val cc = table.columnCount
  while (col < cc) {
    val component = table.getCellRenderer(row, col)
      .getTableCellRendererComponent(table, table.getValueAt(row, col), false, false, row, col)
    val size = component.preferredSize
    height = height.coerceAtLeast(size.height)
    ++col
  }
  return height
}
