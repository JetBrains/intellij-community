package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.ui.TableCell
import java.awt.Graphics
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.min

/** Renders the pinned part of the complete view. */
internal class FrozenStripTable(
  grid: DataGrid,
  columnActions: ActionGroup,
  rowActions: ActionGroup,
) : GridTable(grid, columnActions, rowActions) {
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
}
