package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridHelper
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.GridUtilCore
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.overrideValue
import com.intellij.database.datagrid.request
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.EDIT_MAXIMIZED_GRID_KEY
import com.intellij.database.run.ui.GridTableCellEditor
import com.intellij.database.run.ui.ValueTabInfoProvider
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactoryProvider
import javax.swing.table.TableCellEditor

/**
 * The view's cell editing as the grid allows it: the grid's cell editors and whether a cell or a selection may be edited. It also tells
 * whether the grid's value editor shows the selected cell. Both tables share it. It holds the grid, as [ViewCellRenderers] does, because
 * the grid's editor factories take the grid's cell requests. It asks the grid on each call, since the grid's answers change without an
 * event. The grid answers on cell editing through its result view, which is set after the view is created. The grid of `TableDiffEditor`
 * answers on its own instead.
 */
internal class ViewCellEditors(private val grid: DataGrid, private val editedValue: MultiCellEditValue) {
  /** Whether the grid allows editing a cell in place. */
  fun isCellEditingAllowed(): Boolean = grid.isCellEditingAllowed

  /**
   * The editor of the grid's editor factory for the cell at [row] and [column], or null when no editor edits the cell. A row id or a
   * virtual column has none. A non-null [currentValue] replaces the cell's value in the editor.
   */
  fun editorFor(row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>, currentValue: Any?): TableCellEditor? {
    val actualRequest = grid.request(row, column)
    val cellRequest = if (currentValue != null) actualRequest.overrideValue(currentValue) else actualRequest
    val editorFactory = GridCellEditorFactoryProvider.provideEditorFactory(cellRequest)
    val dataColumn = cellRequest.getColumn()
    val isEditableColumn = dataColumn != null && !GridUtilCore.isRowId(dataColumn) && !GridUtilCore.isVirtualColumn(dataColumn)
    return if (isEditableColumn && editorFactory != null)
      GridTableCellEditor(grid, row, column, editorFactory)
    else null
  }

  /**
   * Whether the open edit of [editor] writes the shared multi-cell value to every selected cell, in both tables. It needs that value, and
   * the selected columns must allow editing together. A unique column allows it only in one row, or with an editor that computes per row.
   */
  fun allowsMultiEdit(editor: TableCellEditor?): Boolean {
    if (editedValue.value == null) return false
    val selection = grid.selectionModel
    val columns = grid.getDataModel(DataAccessType.DATABASE_DATA).getColumns(selection.selectedColumns)
    val helper = GridHelper.get(grid)
    val uniqueColumn = helper.findUniqueColumn(grid, columns)
    val editsPerRow = (editor as? GridTableCellEditor)?.allowsUniqueMultiEdit() == true
    val uniqueOk = uniqueColumn == null || selection.selectedRowCount == 1 || editsPerRow
    return uniqueOk && helper.canEditTogether(grid, columns)
  }

  /**
   * Whether the grid's maximized editor shows its value tab, which shows the selected cell's value. A table then shows no expanded hint for
   * a selected cell. It is read on each call, since the editor opens and switches tabs without telling the view.
   */
  fun valueEditorShowsSelectedValue(): Boolean =
    grid.getUserData(EDIT_MAXIMIZED_GRID_KEY)?.getCurrentTabInfoProvider() is ValueTabInfoProvider
}
