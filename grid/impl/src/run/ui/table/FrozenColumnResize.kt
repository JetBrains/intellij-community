package com.intellij.database.run.ui.table

import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.event.MouseInputAdapter

/**
 * Resizes pinned columns while keeping space for the main table. A drag on the main header's leading edge, beside the strip's
 * divider, resizes the last pinned column. A resize may shrink a pinned column freely, but widens it only as far as [PinnedColumnsFit]
 * lets the strip grow.
 *
 * One instance serves one pinned table, and holds its handle on the main header until that table is dropped.
 */
internal class FrozenColumnResize(
  private val mainTable: GridTable,
  private val pinnedTable: GridTable,
  private val layout: FrozenStripLayout,
) {
  private var resizing = false
  private var resizeCursorShown = false
  private var dragStartScreenX = 0
  private var dragStartWidth = 0

  private val resizeHandle = object : MouseInputAdapter() {
    override fun mouseMoved(e: MouseEvent) {
      if (isOnResizeEdge(e.x)) {
        mainTable.tableHeader.cursor = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
        resizeCursorShown = true
      }
      else if (resizeCursorShown) {
        mainTable.tableHeader.cursor = Cursor.getDefaultCursor()
        resizeCursorShown = false
      }
    }

    override fun mousePressed(e: MouseEvent) {
      val last = lastPinnedColumn()
      if (last == null || !isOnResizeEdge(e.x)) return
      resizing = true
      dragStartScreenX = e.xOnScreen
      dragStartWidth = last.width
      e.consume()
    }

    override fun mouseDragged(e: MouseEvent) {
      if (!resizing) return
      val last = lastPinnedColumn()
      if (last != null) {
        val direction = if (mainTable.componentOrientation.isLeftToRight) 1 else -1
        val requestedWidth = maxOf(JBUI.scale(24), dragStartWidth + direction * (e.xOnScreen - dragStartScreenX))
        val width = constrainPinnedColumnResize(last, requestedWidth)
        last.setColumnWidthByUser(width)
        layout.scrollPane.revalidate()
        layout.scrollPane.repaint()
      }
      e.consume()
    }

    override fun mouseReleased(e: MouseEvent) {
      resizing = false
    }
  }

  init {
    mainTable.tableHeader.addMouseListener(resizeHandle)
    mainTable.tableHeader.addMouseMotionListener(resizeHandle)
  }

  fun uninstallColumnResizeHandle() {
    mainTable.tableHeader.removeMouseListener(resizeHandle)
    mainTable.tableHeader.removeMouseMotionListener(resizeHandle)
    if (resizeCursorShown) mainTable.tableHeader.cursor = Cursor.getDefaultCursor()
  }

  private fun isOnResizeEdge(headerX: Int): Boolean {
    if (mainTable.isTransposed) return false
    // A clipped divider is a viewport edge, not a column edge that can be resized.
    if (layout.pinnedScrollBar?.isVisible == true) return false
    val resizeArea = JBUI.scale(3)
    return if (mainTable.componentOrientation.isLeftToRight) headerX in 0..resizeArea
    else headerX in mainTable.tableHeader.width - resizeArea..mainTable.tableHeader.width
  }

  private fun lastPinnedColumn(): TableResultViewColumn? {
    val columns = pinnedTable.columnModel
    return if (columns.columnCount == 0) null else columns.getColumn(columns.columnCount - 1) as TableResultViewColumn
  }

  /**
   * The width [column] of [table] takes for a user resize to [requestedWidth]. A pinned column shrinks freely, but grows only while the
   * columns fit and never adds to an overflow.
   */
  fun constrainColumnResize(table: GridTable, column: TableResultViewColumn, requestedWidth: Int): Int =
    if (table === pinnedTable) constrainPinnedColumnResize(column, requestedWidth) else requestedWidth

  private fun constrainPinnedColumnResize(column: TableResultViewColumn, requestedWidth: Int): Int {
    val currentWidth = column.columnWidth
    if (requestedWidth <= currentWidth) return requestedWidth
    val availableWidth = layout.availableColumnsWidth()
    if (availableWidth <= 0) return requestedWidth

    val fittingWidth = PinnedColumnsFit.maximumFittingPinnedWidth(
      availableWidth,
      mainTable.columnModel.totalColumnWidth,
    )
    // If the result area was narrowed around an already oversized strip, resizing must not snap a user width down.
    val maximumTotalWidth = if (layout.pinnedScrollBar?.isVisible == true)
      maxOf(fittingWidth, layout.pinnedScrollBar?.maximum ?: 0)
    else
      fittingWidth
    val otherColumnsWidth = layout.pinnedColumnsWidth(except = column)
    return minOf(requestedWidth, maxOf(column.minWidth, maximumTotalWidth - otherColumnsWidth))
  }

  /**
   * The largest part of [delta] that each selected column can grow by while the pinned and unpinned columns still fit, as with a mouse
   * resize. A shrink, or a selection without pinned columns, keeps [delta]. The result is 0 when the columns already overflow.
   */
  fun constrainSelectedColumnWidthDelta(selectedPinnedCount: Int, selectedUnpinnedCount: Int, delta: Int): Int {
    if (delta <= 0 || selectedPinnedCount == 0) return delta

    val availableWidth = layout.availableColumnsWidth()
    if (availableWidth <= 0) return delta
    val pinnedWidth = layout.pinnedColumnsWidth()
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
