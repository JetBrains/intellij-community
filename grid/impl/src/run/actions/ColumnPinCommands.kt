package com.intellij.database.run.actions

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.run.ui.GridColumnPinning
import com.intellij.openapi.util.NlsActions.ActionDescription
import org.jetbrains.annotations.ApiStatus

/**
 * What the grid can do to the pin of a column, and how to do it.
 *
 * The column header menu, the column list and the pin control of a list row all ask here, so one set of
 * rules answers all three. A caller renders the answer. It states no rule of its own.
 */
@ApiStatus.Internal
class ColumnPinCommands(private val grid: DataGrid) {
  private val panel: GridColumnPinning? get() = pinPanel(grid)

  /** The grid that a command acts on, which is none while the view is transposed. */
  private val actable: GridColumnPinning? get() = actablePanel(grid)

  /** Whether a pin item belongs in a menu for [columns], which it does while none of them is pinned. */
  fun offersPin(columns: ModelIndexSet<GridColumn>): Boolean =
    panel?.let { columns.size() > 0 && allPinned(it, columns, false) } == true

  /** Whether an unpin item belongs in a menu for [columns], which it does while all of them are pinned. */
  fun offersUnpin(columns: ModelIndexSet<GridColumn>): Boolean =
    panel?.let { columns.size() > 0 && allPinned(it, columns, true) } == true

  /** Whether a pin up to here item belongs in a menu for [column]. */
  fun offersPinUpToHere(column: ModelIndex<GridColumn>): Boolean = panel?.canPinColumnsUpToHere(column) == true

  /** Whether an unpin all item belongs in a menu. */
  fun offersUnpinAll(): Boolean = panel?.hasPinnedColumns() == true

  /** Returns the columns whose inline pin control can act in the current view. */
  fun columnsThatCanTogglePin(): Set<ModelIndex<GridColumn>> = actable?.columnsThatCanTogglePin() ?: emptySet()

  /** Why a pin of [columns] cannot act, or null when it can. */
  fun reasonPinRefuses(columns: ModelIndexSet<GridColumn>): @ActionDescription String? = when {
    grid.resultView.isTransposed -> DataGridBundle.message(TRANSPOSED)
    panel?.pinnedColumnsFit(columns) == false -> DataGridBundle.message(NO_SPACE)
    else -> null
  }

  /** Why a pin up to [column] cannot act, or null when it can. */
  fun reasonPinUpToHereRefuses(column: ModelIndex<GridColumn>): @ActionDescription String? = when {
    grid.resultView.isTransposed -> DataGridBundle.message(TRANSPOSED)
    panel?.pinnedColumnsUpToHereFit(column) == false -> DataGridBundle.message(NO_SPACE)
    else -> null
  }

  /** Why an unpin cannot act, or null when it can. An unpin needs no width, so only the view stops it. */
  fun reasonUnpinRefuses(): @ActionDescription String? =
    if (grid.resultView.isTransposed) DataGridBundle.message(TRANSPOSED) else null

  /** Pins [columns]. The grid refuses a pin that leaves no usable width, so this can do nothing. */
  fun pin(columns: ModelIndexSet<GridColumn>) {
    actable?.pinColumns(columns)
  }

  fun unpin(columns: ModelIndexSet<GridColumn>) {
    actable?.setColumnsPinned(columns, false)
  }

  fun pinUpToHere(column: ModelIndex<GridColumn>) {
    actable?.pinColumnsUpToHere(column)
  }

  fun unpinAll() {
    actable?.unpinAllColumns()
  }

  /** Unpins [column] when it is pinned, and pins it when it is not. */
  fun togglePin(column: ModelIndex<GridColumn>) {
    val pinning = panel ?: return
    val columns = ModelIndexSet.forColumns(grid, column.asInteger())
    if (pinning.isColumnPinned(column)) unpin(columns) else pin(columns)
  }
}
