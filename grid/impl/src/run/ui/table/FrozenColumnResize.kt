package com.intellij.database.run.ui.table

import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.event.MouseInputAdapter

/**
 * Resizes the last pinned column from the main header's leading edge and keeps resizes from overflowing the pinned table. It lives
 * as long as one pinned table, and holds its handle on the main header meanwhile.
 */
internal class FrozenColumnResize(
  private val mainTable: GridTable,
  private val frozen: GridTable,
  private val layout: FrozenStripLayout,
) {
  private var resizingFrozenColumn = false
  private var frozenResizeCursorShown = false
  private var frozenResizeStartX = 0
  private var frozenResizeStartWidth = 0

  private val frozenColumnResizeHandle = object : MouseInputAdapter() {
    override fun mouseMoved(e: MouseEvent) {
      if (inFrozenResizeZone(e.x)) {
        mainTable.tableHeader.cursor = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
        frozenResizeCursorShown = true
      }
      else if (frozenResizeCursorShown) {
        mainTable.tableHeader.cursor = Cursor.getDefaultCursor()
        frozenResizeCursorShown = false
      }
    }

    override fun mousePressed(e: MouseEvent) {
      val last = lastFrozenColumn()
      if (last == null || !inFrozenResizeZone(e.x)) return
      resizingFrozenColumn = true
      frozenResizeStartX = e.xOnScreen
      frozenResizeStartWidth = last.width
      e.consume()
    }

    override fun mouseDragged(e: MouseEvent) {
      if (!resizingFrozenColumn) return
      val last = lastFrozenColumn()
      if (last != null) {
        val direction = if (mainTable.componentOrientation.isLeftToRight) 1 else -1
        val requestedWidth = maxOf(JBUI.scale(24), frozenResizeStartWidth + direction * (e.xOnScreen - frozenResizeStartX))
        val width = constrainFrozenColumnResize(last, requestedWidth)
        last.setColumnWidthByUser(width)
        layout.scrollPane.revalidate()
        layout.scrollPane.repaint()
      }
      e.consume()
    }

    override fun mouseReleased(e: MouseEvent) {
      resizingFrozenColumn = false
    }
  }

  init {
    mainTable.tableHeader.addMouseListener(frozenColumnResizeHandle)
    mainTable.tableHeader.addMouseMotionListener(frozenColumnResizeHandle)
  }

  fun uninstallColumnResizeHandle() {
    mainTable.tableHeader.removeMouseListener(frozenColumnResizeHandle)
    mainTable.tableHeader.removeMouseMotionListener(frozenColumnResizeHandle)
    if (frozenResizeCursorShown) mainTable.tableHeader.cursor = Cursor.getDefaultCursor()
  }

  private fun inFrozenResizeZone(x: Int): Boolean {
    if (mainTable.isTransposed) return false
    // A clipped divider is a viewport edge, not a column edge that can be resized.
    if (layout.frozenScrollBar?.isVisible == true) return false
    val resizeArea = JBUI.scale(3)
    return if (mainTable.componentOrientation.isLeftToRight) x in 0..resizeArea
    else x in mainTable.tableHeader.width - resizeArea..mainTable.tableHeader.width
  }

  private fun lastFrozenColumn(): TableResultViewColumn? {
    val columns = frozen.columnModel
    return if (columns.columnCount == 0) null else columns.getColumn(columns.columnCount - 1) as TableResultViewColumn
  }

  /** A direct resize may shrink freely, but it may not introduce or increase avoidable pinned-strip overflow. */
  fun constrainFrozenColumnResize(table: GridTable,
                                  column: TableResultViewColumn,
                                  requestedWidth: Int): Int =
    if (table === frozen) constrainFrozenColumnResize(column, requestedWidth) else requestedWidth

  private fun constrainFrozenColumnResize(column: TableResultViewColumn, requestedWidth: Int): Int {
    val currentWidth = column.columnWidth
    if (requestedWidth <= currentWidth) return requestedWidth
    val availableWidth = layout.availableColumnsWidth()
    if (availableWidth <= 0) return requestedWidth

    val fittingWidth = PinnedColumnsFit.maximumFittingPinnedWidth(
      availableWidth,
      mainTable.columnModel.totalColumnWidth,
    )
    // If the result area was narrowed around an already oversized strip, resizing must not snap a user width down.
    val maximumTotalWidth = if (layout.frozenScrollBar?.isVisible == true)
      maxOf(fittingWidth, layout.frozenScrollBar?.maximum ?: 0)
    else
      fittingWidth
    val otherColumnsWidth = layout.frozenColumnsWidth(except = column)
    return minOf(requestedWidth, maxOf(column.minWidth, maximumTotalWidth - otherColumnsWidth))
  }

  /**
   * Keeps the keyboard width action consistent with mouse resizing, including mixed and multi-column selections.
   * Takes how many of the selected columns are pinned and how many are not.
   */
  fun constrainSelectedColumnWidthDelta(selectedPinnedCount: Int, selectedUnpinnedCount: Int, delta: Int): Int {
    if (delta <= 0 || selectedPinnedCount == 0) return delta

    val availableWidth = layout.availableColumnsWidth()
    if (availableWidth <= 0) return delta
    val pinnedWidth = layout.frozenColumnsWidth()
    val unpinnedWidth = mainTable.columnModel.totalColumnWidth
    if (!PinnedColumnsFit.fits(pinnedWidth, unpinnedWidth, availableWidth)) return 0

    var low = 0
    var high = delta
    while (low < high) {
      val candidate = ((low.toLong() + high + 1) / 2).toInt()
      val candidatePinnedWidth = widthAfterDelta(pinnedWidth, selectedPinnedCount, candidate)
      val candidateUnpinnedWidth = widthAfterDelta(unpinnedWidth, selectedUnpinnedCount, candidate)
      if (PinnedColumnsFit.fits(candidatePinnedWidth, candidateUnpinnedWidth, availableWidth)) low = candidate
      else high = candidate - 1
    }
    return low
  }

  private fun widthAfterDelta(width: Int, columnCount: Int, delta: Int): Int =
    (width.toLong() + columnCount.toLong() * delta).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
