package com.intellij.database.run.actions

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.run.ui.TableResultPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent

private fun pinPanel(grid: DataGrid): TableResultPanel? =
  if (TableResultPanel.isColumnPinningEnabled()) grid as? TableResultPanel else null

private fun allPinned(panel: TableResultPanel, columns: ModelIndexSet<GridColumn>, pinned: Boolean): Boolean =
  columns.asIterable().all { panel.isColumnPinned(it) == pinned }

/** Targets the whole selection when the right-clicked header is part of a multi-column one, not just that column. */
private fun pinTargetColumns(grid: DataGrid, base: ModelIndexSet<GridColumn>): ModelIndexSet<GridColumn> {
  val context = grid.contextColumn
  if (context.value == -1) return base
  val selected = grid.selectionModel.selectedColumns
  return if (selected.size() > 1 && selected.asIterable().any { it.value == context.value }) selected else base
}

/** Pins the selected columns; shown only when none of them is pinned yet (no action on a mixed selection). */
class PinColumnsAction : ColumnHeaderActionBase(true) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun getColumns(grid: DataGrid): ModelIndexSet<GridColumn> = pinTargetColumns(grid, super.getColumns(grid))

  override fun update(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val panel = pinPanel(grid)
    e.presentation.isEnabledAndVisible = panel != null && columnIdxs.size() > 0 && allPinned(panel, columnIdxs, false)
    e.presentation.text = DataGridBundle.message(if (columnIdxs.size() == 1) "action.Console.TableResult.PinColumn.text"
                                                 else "action.Console.TableResult.PinColumns.text")
  }

  override fun actionPerformed(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    pinPanel(grid)?.setColumnsPinned(columnIdxs, true)
  }
}

/** Unpins the selected columns; shown only when all of them are pinned. */
class UnpinColumnsAction : ColumnHeaderActionBase(true) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun getColumns(grid: DataGrid): ModelIndexSet<GridColumn> = pinTargetColumns(grid, super.getColumns(grid))

  override fun update(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val panel = pinPanel(grid)
    e.presentation.isEnabledAndVisible = panel != null && columnIdxs.size() > 0 && allPinned(panel, columnIdxs, true)
    e.presentation.text = DataGridBundle.message(if (columnIdxs.size() == 1) "action.Console.TableResult.UnpinColumn.text"
                                                 else "action.Console.TableResult.UnpinColumns.text")
  }

  override fun actionPerformed(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    pinPanel(grid)?.setColumnsPinned(columnIdxs, false)
  }
}

/** Pins every column from the first one up to and including the clicked one. */
class PinColumnsUpToHereAction : ColumnHeaderActionBase() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val panel = pinPanel(grid)
    val column = if (columnIdxs.size() == 1) columnIdxs.asIterable().first() else null
    e.presentation.isEnabledAndVisible = panel != null && column != null && panel.canPinColumnsUpToHere(column)
  }

  override fun actionPerformed(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val panel = pinPanel(grid) ?: return
    val column = columnIdxs.asIterable().firstOrNull() ?: return
    panel.pinColumnsUpToHere(column)
  }
}

/** Unpins all columns; shown only when at least one column is pinned. */
class UnpinAllColumnsAction : ColumnHeaderActionBase() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val panel = pinPanel(grid)
    e.presentation.isEnabledAndVisible = panel != null && panel.hasPinnedColumns()
  }

  override fun actionPerformed(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    pinPanel(grid)?.unpinAllColumns()
  }
}
