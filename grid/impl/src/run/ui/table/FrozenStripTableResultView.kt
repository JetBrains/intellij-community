package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.ui.TableCell
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseEvent
import kotlin.math.max
import kotlin.math.min

/**
 * The pinned strip beside the grid's own table. It shares the grid data and the look, but none of the grid-global
 * parts, so it is the plain table of the pair. It draws the divider on its trailing edge and hands column selection
 * to the table that owns it.
 */
internal class FrozenStripTableResultView(
  resultPanel: DataGrid,
  columnHeaderPopupActions: ActionGroup,
  rowHeaderPopupActions: ActionGroup,
  frozenColumnsController: FrozenColumnsController,
) : TableResultView(resultPanel, columnHeaderPopupActions, rowHeaderPopupActions, frozenColumnsController) {

  override fun getOwningPrimaryView(): TableResultView? = frozenColumnsControllerIfBuilt?.getPrimaryView()

  override fun paintTrailingDivider(g: Graphics, bottom: Int) {
    paintPinDivider(g, this, bottom)
  }

  /** Include grid spacing so the hint cannot cover the pointer and trigger a show/hide loop. */
  override fun narrowExpandedCellHint(visible: Rectangle, key: TableCell): Rectangle {
    val cell = getCellRect(key.row, key.column, true)
    val table = visibleRect
    val right = min(cell.x + cell.width, table.x + table.width)
    visible.width = max(0, right - visible.x)
    return visible
  }

  /** Strip columns are selected in the primary table, which owns the unified column selection. */
  override fun forwardColumnSelection(viewColumn: Int, e: MouseEvent, primaryView: TableResultView): Boolean {
    if (primaryView === this) return true
    val primaryColumn = toViewColumnIn(viewColumn, primaryView)
    if (primaryColumn >= 0) primaryView.selectViewColumnInterval(primaryColumn, e)
    return true
  }
}
