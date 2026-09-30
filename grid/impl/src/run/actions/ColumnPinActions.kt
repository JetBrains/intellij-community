package com.intellij.database.run.actions

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.run.ui.GridColumnPinning
import com.intellij.database.run.ui.table.ColumnPinning
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.NlsActions.ActionDescription

internal const val TRANSPOSED: String = "action.Console.TableResult.PinColumns.transposed.description"
internal const val NO_SPACE: String = "action.Console.TableResult.PinColumns.insufficient.space.description"

/** Returns the grid as a pinning one when column pinning is enabled. */
internal fun pinPanel(grid: DataGrid): GridColumnPinning? =
  (grid as? GridColumnPinning)?.takeIf { ColumnPinning.isEnabled() }

/** Rechecks availability at invocation because the presentation may be stale. */
internal fun actablePanel(grid: DataGrid): GridColumnPinning? = pinPanel(grid)?.takeIf { !grid.resultView.isTransposed }

internal fun allPinned(panel: GridColumnPinning, columns: ModelIndexSet<GridColumn>, pinned: Boolean): Boolean =
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
internal fun AnAction.showReason(e: AnActionEvent, reason: @ActionDescription String?) {
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
    val commands = ColumnPinCommands(grid)
    // Offer unpin actions for pinned columns.
    if (!commands.offersPin(columnIdxs)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    showReason(e, commands.reasonPinRefuses(columnIdxs))
  }

  override fun actionPerformed(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    ColumnPinCommands(grid).pin(columnIdxs)
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
    val commands = ColumnPinCommands(grid)
    if (!commands.offersUnpin(columnIdxs)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    showReason(e, commands.reasonUnpinRefuses())
  }

  override fun actionPerformed(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    ColumnPinCommands(grid).unpin(columnIdxs)
  }
}

/** Pins the prefix through the clicked column; offered only on an unpinned target. */
class PinColumnsUpToHereAction : ColumnHeaderActionBase() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val commands = ColumnPinCommands(grid)
    val column = columnIdxs.asIterable().singleOrNull()
    if (column == null || !commands.offersPinUpToHere(column)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    showReason(e, commands.reasonPinUpToHereRefuses(column))
  }

  override fun actionPerformed(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val column: ModelIndex<GridColumn> = columnIdxs.asIterable().firstOrNull() ?: return
    ColumnPinCommands(grid).pinUpToHere(column)
  }
}

/** Unpins all columns. */
class UnpinAllColumnsAction : ColumnHeaderActionBase() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val commands = ColumnPinCommands(grid)
    if (!commands.offersUnpinAll()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    showReason(e, commands.reasonUnpinRefuses())
  }

  override fun actionPerformed(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    ColumnPinCommands(grid).unpinAll()
  }
}
