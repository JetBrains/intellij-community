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

/** Null when the grid has no column pinning at all, which keeps these actions out of the menu. */
private fun pinPanel(grid: DataGrid): TableResultPanel? =
  (grid as? TableResultPanel)?.takeIf { TableResultPanel.isColumnPinningEnabled() }

/** The panel an invocation may act on: a presentation can go stale, so the conditions are re-read here. */
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
 * Shows the action disabled with [reason] instead of hiding it, and restores its normal description once the reason
 * stops applying. A null reason means the action is available.
 *
 * The reason doubles as the tooltip: a disabled popup item is not selectable, so its description alone never reaches
 * the status bar.
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
    // Pinned columns are offered the unpin actions instead, so pinning is hidden rather than explained.
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
    // Unpinning is noise on columns that are not pinned, so it is hidden rather than explained.
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

/** Pins every column from the first one up to and including the clicked one; not offered once they all are. */
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
    // Nothing left to pin up to here, or nothing to count it along: either way the action is not offered.
    if (!panel.isColumnInDisplayOrder(column) || !panel.canPinColumnsUpToHere(column)) {
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
