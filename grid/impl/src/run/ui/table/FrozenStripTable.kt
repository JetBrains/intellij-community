package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.ui.TableCell
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.ListSelectionModel
import javax.swing.table.TableColumnModel
import kotlin.math.max
import kotlin.math.min

/**
 * Renders the pinned part of the complete view.
 *
 * @param viewDataModel the view's data model, the same instance the main table shows; the pinned table has no model of its own
 * @param rowSelection the row selection it starts with; the view gives it the shared one once it is set up
 * @param columns the column model it starts with; the view gives it its slice of the view's columns once it is set up
 */
internal class FrozenStripTable(
  private val grid: DataGrid,
  viewDataModel: GridTableModel,
  rowSelection: ListSelectionModel,
  columns: TableColumnModel,
  columnCache: MyTableColumnCache,
  columnActions: ActionGroup,
  rowActions: ActionGroup,
) : GridTable(grid, viewDataModel, rowSelection, columns, columnCache, columnActions, rowActions) {
  /**
   * The pinned strip owns the pixel column the divider occupies, so any partial repaint that starts inside the strip paints the
   * divider again. An overlay in an ancestor is skipped by such repaints and gets erased or ghosted. The line follows the visible
   * rect, so the hosting viewport must not blit, or a scroll copies it along.
   */
  override fun paintTrailingDivider(g: Graphics, bottom: Int) {
    val dividerWidth = JBUIScale.scale(PIN_DIVIDER_WIDTH)
    val visible = visibleRect
    val leadingEdge = max(0, visible.x)
    val trailingEdge = min(width, visible.x + visible.width)
    val x = if (componentOrientation.isLeftToRight) max(leadingEdge, trailingEdge - dividerWidth) else leadingEdge
    g.color = JBUI.CurrentTheme.EditorTabs.underlineColor()
    g.fillRect(x, 0, dividerWidth, min(bottom, height))
  }

  /** A press or a release in the pinned table must not scroll the main table's viewport through the selection it changes. */
  override fun processSwingMouseEvent(e: MouseEvent) {
    runWithAutoscrollLocked(grid) {
      super.processSwingMouseEvent(e)
      // The column selection is the view's, through the slice: a release ends the adjusting change of a drag.
      if (e.id == MouseEvent.MOUSE_RELEASED) columnModel.selectionModel.valueIsAdjusting = false
    }
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

/** Unscaled width of the divider the pinned strip paints on its trailing edge. */
private const val PIN_DIVIDER_WIDTH = 1
