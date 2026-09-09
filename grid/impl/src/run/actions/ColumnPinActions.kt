package com.intellij.database.run.actions

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.run.ui.TableResultPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.NlsActions.ActionDescription

private const val TRANSPOSED = "action.Console.TableResult.PinColumns.transposed.description"
private const val NO_SPACE = "action.Console.TableResult.PinColumns.insufficient.space.description"

/** Returns the panel when column pinning is enabled. */
private fun pinPanel(grid: DataGrid): TableResultPanel? =
  (grid as? TableResultPanel)?.takeIf { TableResultPanel.isColumnPinningEnabled() }

/** Rechecks availability at invocation because the presentation may be stale. */
private fun actablePanel(grid: DataGrid): TableResultPanel? = pinPanel(grid)?.takeIf { !it.resultView.isTransposed }

private fun allPinned(panel: TableResultPanel, columns: ModelIndexSet<GridColumn>, pinned: Boolean): Boolean =
  columns.asIterable().all { panel.isColumnPinned(it) == pinned }

/** Targets the whole selection when the right-clicked header is part of one. */
private fun pinTargetColumns(grid: DataGrid, base: ModelIndexSet<GridColumn>): ModelIndexSet<GridColumn> {
  val context = grid.contextColumn
  if (context.value == -1) return base
  val selected = grid.selectionModel.selectedColumns
  return if (selected.size() > 1 && selected.asIterable().any { it.value == context.value }) selected else base
}

/**
 * Disables the action with [reason], or restores its normal description when available.
 *
 * Use a tooltip too: disabled popup items cannot show their description in the status bar.
 */
private fun AnAction.showReason(e: AnActionEvent, reason: @ActionDescription String?) {
  e.presentation.isVisible = true
  e.presentation.isEnabled = reason == null
  e.presentation.description = reason ?: templatePresentation.description
  e.presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, reason)
}

/** Pins the selected columns; not offered while any of them is already pinned. */
class PinColumnsAction : ColumnHeaderActionBase(true) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun getColumns(grid: DataGrid): ModelIndexSet<GridColumn> = pinTargetColumns(grid, super.getColumns(grid))

  override fun update(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val one = columnIdxs.size() == 1
    e.presentation.text = DataGridBundle.message(if (one) "action.Console.TableResult.PinColumn.text"
                                                 else "action.Console.TableResult.PinColumns.text")
    val panel = pinPanel(grid)
    if (panel == null || columnIdxs.size() == 0) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (panel.resultView.isTransposed) {
      showReason(e, DataGridBundle.message(TRANSPOSED))
      return
    }
    // Offer unpin actions for pinned columns.
    if (!allPinned(panel, columnIdxs, false)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    showReason(e, if (panel.pinnedColumnsFit(columnIdxs)) null else DataGridBundle.message(NO_SPACE))
  }

  override fun actionPerformed(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    actablePanel(grid)?.pinColumns(columnIdxs)
  }
}

/** Unpins the selected columns. */
class UnpinColumnsAction : ColumnHeaderActionBase(true) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun getColumns(grid: DataGrid): ModelIndexSet<GridColumn> = pinTargetColumns(grid, super.getColumns(grid))

  override fun update(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val one = columnIdxs.size() == 1
    e.presentation.text = DataGridBundle.message(if (one) "action.Console.TableResult.UnpinColumn.text"
                                                 else "action.Console.TableResult.UnpinColumns.text")
    // Offer this action only when all target columns are pinned.
    val panel = pinPanel(grid)
    if (panel == null || columnIdxs.size() == 0 || !allPinned(panel, columnIdxs, true)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    showReason(e, if (panel.resultView.isTransposed) DataGridBundle.message(TRANSPOSED) else null)
  }

  override fun actionPerformed(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    actablePanel(grid)?.setColumnsPinned(columnIdxs, false)
  }
}

/** Pins the prefix through the clicked column; offered only on an unpinned target. */
class PinColumnsUpToHereAction : ColumnHeaderActionBase() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val panel = pinPanel(grid)
    val column = columnIdxs.asIterable().singleOrNull()
    if (panel == null || column == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (panel.resultView.isTransposed) {
      showReason(e, DataGridBundle.message(TRANSPOSED))
      return
    }
    if (!panel.canPinColumnsUpToHere(column)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    showReason(e, if (panel.pinnedColumnsUpToHereFit(column)) null else DataGridBundle.message(NO_SPACE))
  }

  override fun actionPerformed(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val panel = actablePanel(grid) ?: return
    val column: ModelIndex<GridColumn> = columnIdxs.asIterable().firstOrNull() ?: return
    panel.pinColumnsUpToHere(column)
  }
}

/** Unpins all columns. */
class UnpinAllColumnsAction : ColumnHeaderActionBase() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val panel = pinPanel(grid)
    if (panel == null || !panel.hasPinnedColumns()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    showReason(e, if (panel.resultView.isTransposed) DataGridBundle.message(TRANSPOSED) else null)
  }

  override fun actionPerformed(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    actablePanel(grid)?.unpinAllColumns()
  }
}
