package com.intellij.database.run.ui

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.CellColors
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridMutator
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.mutating.CellMutation
import com.intellij.database.run.ui.grid.CellAttributesKey
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactoryProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.sql.Types
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellRenderer

private class ArrayRow(var value: Any?, val isAdded: Boolean, var isDeleted: Boolean, val committedIndex: Int)

private class ArrayCellState(
  val row: ModelIndex<GridRow>,
  val col: ModelIndex<GridColumn>,
  val committedElements: List<Any?>,
  val originalValue: Any?,
  val rows: MutableList<ArrayRow>,
) {
  fun hasChanges(): Boolean =
    rows.any { it.isAdded || it.isDeleted ||
               (it.committedIndex >= 0 && it.committedIndex < committedElements.size &&
                it.value != committedElements[it.committedIndex]) }

  fun getValue(): Any? = if (hasChanges()) unwrap() else originalValue

  private fun unwrap(): Any = when (originalValue) {
    is Set<*>  -> LinkedHashSet<Any?>().also { set -> for (r in rows) if (!r.isDeleted) set.add(r.value) }
    is List<*> -> ArrayList<Any?>().also { list -> for (r in rows) if (!r.isDeleted) list.add(r.value) }
    else -> {
      val count = rows.count { !it.isDeleted }
      var i = 0
      val arr = arrayOfNulls<Any>(count)
      for (r in rows) if (!r.isDeleted) arr[i++] = r.value
      arr
    }
  }
}

class ArrayGridViewer(private val grid: DataGrid) : CellViewer {
  private val panel = JPanel(BorderLayout())
  private var rowIdx: ModelIndex<GridRow> = ModelIndex.forRow(grid, -1)
  private var columnIdx: ModelIndex<GridColumn> = ModelIndex.forColumn(grid, -1)

  private var displayedState: ArrayCellState? = null
  private var displayedIsEditable: Boolean = false

  override val component: JComponent = panel
  override val preferedFocusComponent: JComponent? = null

  override fun update(event: UpdateEvent?) {
    rowIdx    = grid.selectionModel.leadSelectionRow
    columnIdx = grid.selectionModel.leadSelectionColumn
    if (!rowIdx.isValid(grid) || !columnIdx.isValid(grid) || !isArrayColumn()) {
      showEmpty(); return
    }

    val storedState = (grid.dataHookup.mutator as? GridMutator.DatabaseMutator<GridRow, GridColumn>)
      ?.getMutation(rowIdx, columnIdx)?.metadata as? ArrayCellState
    if (storedState != null) {
      val isEditable = computeIsEditable(storedState.originalValue)
      if (storedState === displayedState && isEditable == displayedIsEditable) return
      showTable(storedState, isEditable)
      return
    }

    val existing = displayedState
    if (existing != null && existing.row == rowIdx && existing.col == columnIdx && !existing.hasChanges()) {
      val isEditable = computeIsEditable(existing.originalValue)
      if (isEditable == displayedIsEditable) return
      showTable(existing, isEditable)
      return
    }

    val value: Any? = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getValueAt(rowIdx, columnIdx)
    val isEditable = computeIsEditable(value)
    val elements = toList(value) ?: emptyList()
    val rows = elements.mapIndexedTo(ArrayList(elements.size)) { i, v ->
      ArrayRow(v, isAdded = false, isDeleted = false, committedIndex = i)
    }
    showTable(ArrayCellState(rowIdx, columnIdx, elements, value, rows), isEditable)
  }

  private fun computeIsEditable(value: Any?): Boolean {
    if (!grid.isEditable) return false
    val row = rowIdx
    val column = columnIdx
    if (!row.isValid(grid) || !column.isValid(grid)) return false
    val factory = GridCellEditorFactoryProvider.get(grid)?.getEditorFactory(grid, row, column) ?: return false
    return factory.isEditableChecker.isEditable(value, grid, column)
  }

  private fun showTable(state: ArrayCellState, isEditable: Boolean) {
    clearCenter()
    displayedState = state
    displayedIsEditable = isEditable
    val rows = state.rows
    val committed = state.committedElements
    val model = ArrayTableModel(rows, isEditable) { propagateToGrid(state) }
    val table = object : JBTable(model) {
      override fun prepareRenderer(renderer: TableCellRenderer, row: Int, column: Int): Component {
        val arrayRow = rows[row]
        val c = renderer.getTableCellRendererComponent(this, arrayRow.value, isRowSelected(row), false, row, column)
        if (c is JComponent) {
          if (!isRowSelected(row)) {
            val mutationKey: CellAttributesKey? = when {
              arrayRow.isDeleted -> CellColors.REMOVE
              arrayRow.isAdded -> CellColors.INSERT
              arrayRow.committedIndex < committed.size && arrayRow.value != committed[arrayRow.committedIndex] -> CellColors.REPLACE
              else -> null
            }
            c.background = mutationKey?.let { grid.getColorsScheme().getAttributes(it).backgroundColor }
                           ?: background
          }
          c.border = JBUI.Borders.empty(0, 16)
        }
        return c
      }
    }
    table.tableHeader = null
    table.setShowVerticalLines(false)
    (grid.resultView as? ResultViewWithRows)?.rowHeight?.let { table.rowHeight = it }
    val decorator = ToolbarDecorator.createDecorator(table)
    val revertAction = object : DumbAwareAction(DataGridBundle.message("EditMaximized.ArrayGrid.revert"), null, AllIcons.General.Reset) {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
      override fun update(e: AnActionEvent) {
        val i = table.selectedRow
        if (i < 0) {
          e.presentation.isEnabled = false; return
        }
        val row = rows[i]
        e.presentation.isEnabled = row.isAdded || row.isDeleted ||
                                   (row.committedIndex < committed.size && row.value != committed[row.committedIndex])
      }

      override fun actionPerformed(e: AnActionEvent) {
        val i = table.selectedRow
        if (i < 0) return
        val row = rows[i]
        when {
          row.isAdded -> {
            rows.removeAt(i); model.fireTableRowsDeleted(i, i)
          }
          row.isDeleted -> {
            row.isDeleted = false; model.fireTableRowsUpdated(i, i)
          }
          row.committedIndex < committed.size -> {
            row.value = committed[row.committedIndex]; model.fireTableRowsUpdated(i, i)
          }
          else -> return
        }
        propagateToGrid(state)
      }
    }
    decorator
      .setAddAction {
        val insertAt = if (table.selectedRow >= 0) table.selectedRow + 1 else rows.size
        rows.add(insertAt, ArrayRow(null, isAdded = true, isDeleted = false, committedIndex = -1))
        model.fireTableRowsInserted(insertAt, insertAt)
        table.selectionModel.setSelectionInterval(insertAt, insertAt)
        propagateToGrid(state)
      }
      .setRemoveAction {
        val i = table.selectedRow
        if (i < 0) return@setRemoveAction
        val row = rows[i]
        if (row.isAdded) {
          rows.removeAt(i)
          model.fireTableRowsDeleted(i, i)
        }
        else {
          row.isDeleted = !row.isDeleted
          model.fireTableRowsUpdated(i, i)
        }
        propagateToGrid(state)
      }
      .addExtraAction(revertAction)

    val decoratorPanel = decorator.createPanel()
    decoratorPanel.border = JBUI.Borders.empty()
    panel.add(decoratorPanel, BorderLayout.CENTER)
    decorator.actionsPanel?.apply {
      setCustomShortcuts(CommonActionsPanel.Buttons.ADD, CustomShortcutSet.EMPTY)
      setCustomShortcuts(CommonActionsPanel.Buttons.REMOVE, CustomShortcutSet.EMPTY)
      border = JBUI.Borders.compound(IdeBorderFactory.createBorder(JBColor.border(), SideBorder.BOTTOM),
                                     BorderFactory.createEmptyBorder(0, 8, 0, 8))
    }
    panel.revalidate()
    panel.repaint()
  }

  private fun showEmpty() {
    displayedState = null
    displayedIsEditable = false
    clearCenter()
    val empty = JBPanelWithEmptyText()
    empty.withEmptyText(DataGridBundle.message("EditMaximized.ArrayGrid.not.array"))
    panel.add(empty, BorderLayout.CENTER)
    panel.revalidate()
    panel.repaint()
  }

  private fun clearCenter() {
    val center = (panel.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)
    if (center != null) panel.remove(center)
  }

  private fun propagateToGrid(state: ArrayCellState) {
    val row = rowIdx
    val col = columnIdx
    if (!row.isValid(grid) || !col.isValid(grid)) return
    grid.dataHookup.mutator?.mutate(
      GridRequestSource(EditMaximizedViewRequestPlace(grid, this)),
      listOf(CellMutation(row, col, state.getValue()).withMetadata(state)),
      false
    )
  }

  private fun isArrayColumn(): Boolean {
    val col = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(columnIdx) ?: return false
    return isArrayColumnType(col)
  }

  override fun dispose(): Unit = Unit
}

class ArrayGridCellViewerFactory : CellViewerFactory {
  override fun getSuitability(grid: DataGrid, row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>): Suitability {
    if (!Registry.`is`("database.new.arrays.editor", false)) return Suitability.NONE
    if (GridUtil.getSettings(grid)?.isEditArrayAsText ?: false) return Suitability.NONE
    if (!row.isValid(grid) || !column.isValid(grid)) return Suitability.NONE
    val col = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(column) ?: return Suitability.NONE
    if (!isArrayColumnType(col)) return Suitability.NONE
    val value = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getValueAt(row, column)
    if (isMultiDimensional(value)) return Suitability.NONE
    return Suitability.MAX
  }

  override fun createViewer(grid: DataGrid): CellViewer = ArrayGridViewer(grid)
}

private fun isMultiDimensional(value: Any?): Boolean {
  val iterable: Iterable<*> = when (value) {
    is Array<*>      -> value.asIterable()
    is Collection<*> -> value
    else             -> return false
  }
  return iterable.any { it is Array<*> || it is Collection<*> }
}

private fun toList(value: Any?): List<Any?>? = when (value) {
  is Array<*> -> value.toList()
  is List<*> -> value
  is Set<*> -> value.toList()
  else -> null
}

// TODO: use better way to check type
private fun isArrayColumnType(col: GridColumn): Boolean {
  if (col.type == Types.ARRAY) return true
  val name = col.typeName?.lowercase() ?: return false
  return "array" in name || name.startsWith("list<") || name.startsWith("set<")
}

private fun parseElement(text: String, original: Any?): Any? = when {
  text.isEmpty() -> null
  original is Int -> text.toIntOrNull() ?: text
  original is Long -> text.toLongOrNull() ?: text
  original is Double -> text.toDoubleOrNull() ?: text
  original is Float -> text.toFloatOrNull() ?: text
  original is Boolean -> text.toBooleanStrictOrNull() ?: text
  else -> text
}

private class ArrayTableModel(
  private val rows: MutableList<ArrayRow>,
  private val isEditable: Boolean,
  private val onChange: () -> Unit,
) : AbstractTableModel() {
  override fun getRowCount(): Int = rows.size
  override fun getColumnCount(): Int = 1
  override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = isEditable && !rows[rowIndex].isDeleted
  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = rows[rowIndex].value

  override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
    val text = aValue?.toString() ?: return
    val newValue = parseElement(text, rows[rowIndex].value)
    if (newValue == rows[rowIndex].value) return
    rows[rowIndex].value = newValue
    fireTableCellUpdated(rowIndex, columnIndex)
    onChange()
  }
}
