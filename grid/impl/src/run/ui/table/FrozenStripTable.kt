package com.intellij.database.run.ui.table

import com.intellij.ui.TableCell
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.table.TableColumnModel
import kotlin.math.max
import kotlin.math.min

/**
 * The pinned table. It shows the pinned columns at the leading edge, where they stay while the main table scrolls sideways. Like the main
 * table, it shows the view's data model, row order and row selection. It draws the divider between the two tables.
 *
 * @param sharedParts what the view shares with each table; the data model is the view's, since the pinned table has none of its own
 * @param columnModel a placeholder, since JTable's constructor clears its selection; after its setup, the view sets the table's slice
 */
internal class FrozenStripTable(
  sharedParts: SharedTableParts,
  columnModel: TableColumnModel,
) : GridTable(sharedParts, columnModel) {
  /**
   * Draws the divider to the main table on this table's trailing edge. The table paints it itself, so a partial repaint that starts inside
   * the table paints it again. An overlay in an ancestor would be erased or ghosted. The line follows the visible rect, so the hosting
   * viewport must not blit, or a scroll copies it along.
   */
  override fun paintTrailingDivider(g: Graphics, bottom: Int) {
    val dividerWidth = JBUIScale.scale(PIN_DIVIDER_WIDTH)
    val visible = visibleRect
    val left = max(0, visible.x)
    val right = min(width, visible.x + visible.width)
    val x = if (componentOrientation.isLeftToRight) max(left, right - dividerWidth) else left
    g.color = JBUI.CurrentTheme.EditorTabs.underlineColor()
    g.fillRect(x, 0, dividerWidth, min(bottom, height))
  }

  /** A press or a release in the pinned table must not scroll the main table's viewport through the selection it changes. */
  override fun processSwingMouseEvent(e: MouseEvent) {
    runWithAutoscrollLocked(autoscrollLocker) {
      super.processSwingMouseEvent(e)
      // The column selection is the view's, through the slice: a release ends the adjusting change of a drag.
      if (e.id == MouseEvent.MOUSE_RELEASED) columnModel.selectionModel.valueIsAdjusting = false
    }
  }

  /**
   * Ends the hint's visible area at the cell's right edge, spacing included, or at the table's visible edge if that comes first. With the
   * spacing, the hint cannot cover the pointer and trigger a show/hide loop.
   */
  override fun narrowExpandedCellHint(visible: Rectangle, key: TableCell): Rectangle {
    val cell = getCellRect(key.row, key.column, true)
    val shown = visibleRect
    val right = min(cell.x + cell.width, shown.x + shown.width)
    visible.width = max(0, right - visible.x)
    return visible
  }
}

/** Unscaled width of the divider the pinned strip paints on its trailing edge. */
private const val PIN_DIVIDER_WIDTH = 1
