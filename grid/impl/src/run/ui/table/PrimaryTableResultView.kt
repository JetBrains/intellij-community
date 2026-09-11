package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.run.ui.ResultViewWithFrozenColumns
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.util.Disposer
import java.awt.Component

/**
 * The table the grid owns. It carries the grid-global parts the strip must not have, and it is the only side that
 * can hold a frozen strip, which is why [ResultViewWithFrozenColumns] lives here and not on the base class.
 */
class PrimaryTableResultView(
  resultPanel: DataGrid,
  columnHeaderPopupActions: ActionGroup,
  rowHeaderPopupActions: ActionGroup,
) : TableResultView(resultPanel, columnHeaderPopupActions, rowHeaderPopupActions, null), ResultViewWithFrozenColumns {

  init {
    installGridIntegration()
  }

  override fun isCellComponent(component: Component?): Boolean = frozenColumnsController.isCellComponent(component)

  override fun getFrozenView(): TableResultView? = frozenColumnsControllerIfBuilt?.frozenView

  /** A pinned cell edits in the strip, so the grid still counts as editing while this table holds no editor. */
  override fun isEditingAnywhere(): Boolean = isEditing || frozenColumnsController.isEditingInFrozenView()

  override fun showRowNumbers(v: Boolean) {
    frozenColumnsController.showRowNumbers(v)
  }

  override fun onTransposed() {
    frozenColumnsController.refreshRowNumbers()
  }

  /**
   * Shows [pinnedColumns] in a frozen leading region that stays fixed while the rest scroll horizontally; an empty
   * set removes the region. The pinned columns keep their place in this view and are rendered at width 0 here, their
   * real width living in the frozen view, so pinning never changes the column order.
   */
  override fun setFrozenColumns(pinnedColumns: Collection<ModelIndex<GridColumn>>) {
    frozenColumnsController.setFrozenColumns(pinnedColumns)
  }

  /** The strip sits in the row header, outside the scrollable viewport, so its width counts too. */
  override fun getAvailableColumnsWidth(): Int = frozenColumnsController.getAvailableColumnsWidth()

  override fun dropFrozenColumns() {
    setFrozenColumns(emptyList())
  }

  override fun dispose() {
    super.dispose()
    Disposer.dispose(frozenColumnsController)
  }
}
