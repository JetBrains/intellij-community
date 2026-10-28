package com.intellij.database.run.ui.table

import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.event.MouseInputAdapter

/** Resizes the last pinned column from the main header's leading edge and keeps resizes from overflowing the pinned table. */
internal class FrozenColumnResize(
  private val primaryView: GridTable,
  private val currentFrozenView: () -> GridTable?,
  private val layout: FrozenStripLayout,
) {
  private val frozenView: GridTable? get() = currentFrozenView()

  private var resizingFrozenColumn = false
  private var frozenResizeCursorShown = false
  private var frozenResizeStartX = 0
  private var frozenResizeStartWidth = 0
  private var frozenColumnResizeHandle: MouseInputAdapter? = null

  fun installColumnResizeHandle() {
    if (frozenColumnResizeHandle != null) return
    val handle = object : MouseInputAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        if (inFrozenResizeZone(e.x)) {
          primaryView.tableHeader.cursor = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
          frozenResizeCursorShown = true
        }
        else if (frozenResizeCursorShown) {
          primaryView.tableHeader.cursor = Cursor.getDefaultCursor()
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
          val direction = if (primaryView.componentOrientation.isLeftToRight) 1 else -1
          val requestedWidth = maxOf(JBUI.scale(24), frozenResizeStartWidth + direction * (e.xOnScreen - frozenResizeStartX))
          val width = constrainFrozenColumnResize(last, requestedWidth)
          last.setColumnWidthByUser(width)
          layout.findScrollPane()?.let {
            it.revalidate()
            it.repaint()
          }
        }
        e.consume()
      }

      override fun mouseReleased(e: MouseEvent) {
        resizingFrozenColumn = false
      }
    }
    frozenColumnResizeHandle = handle
    primaryView.tableHeader.addMouseListener(handle)
    primaryView.tableHeader.addMouseMotionListener(handle)
  }

  fun uninstallColumnResizeHandle() {
    frozenColumnResizeHandle?.let {
      primaryView.tableHeader.removeMouseListener(it)
      primaryView.tableHeader.removeMouseMotionListener(it)
    }
    frozenColumnResizeHandle = null
  }

  private fun inFrozenResizeZone(x: Int): Boolean {
    if (frozenView == null || primaryView.isTransposed) return false
    // A clipped divider is a viewport edge, not a column edge that can be resized.
    if (layout.frozenScrollBar?.isVisible == true) return false
    val resizeArea = JBUI.scale(3)
    return if (primaryView.componentOrientation.isLeftToRight) x in 0..resizeArea
    else x in primaryView.tableHeader.width - resizeArea..primaryView.tableHeader.width
  }

  private fun lastFrozenColumn(): TableResultViewColumn? {
    val columns = frozenView?.columnModel ?: return null
    return if (columns.columnCount == 0) null else columns.getColumn(columns.columnCount - 1) as TableResultViewColumn
  }

  /** A direct resize may shrink freely, but it may not introduce or increase avoidable pinned-strip overflow. */
  fun constrainFrozenColumnResize(view: GridTable,
                                  column: TableResultViewColumn,
                                  requestedWidth: Int): Int =
    if (view === frozenView) constrainFrozenColumnResize(column, requestedWidth) else requestedWidth

  private fun constrainFrozenColumnResize(column: TableResultViewColumn, requestedWidth: Int): Int {
    val currentWidth = column.columnWidth
    if (requestedWidth <= currentWidth) return requestedWidth
    val parent = layout.findScrollPane() ?: return requestedWidth
    val availableWidth = layout.availableColumnsWidth(parent)
    if (availableWidth <= 0) return requestedWidth

    val fittingWidth = PinnedColumnsFit.maximumFittingPinnedWidth(
      availableWidth,
      primaryView.columnModel.totalColumnWidth,
    )
    // If the result area was narrowed around an already oversized strip, resizing must not snap a user width down.
    val maximumTotalWidth = if (layout.frozenScrollBar?.isVisible == true)
      maxOf(fittingWidth, layout.frozenScrollBar?.maximum ?: 0)
    else
      fittingWidth
    val otherColumnsWidth = frozenView?.let { layout.frozenColumnsWidth(it, except = column) } ?: 0
    return minOf(requestedWidth, maxOf(column.minWidth, maximumTotalWidth - otherColumnsWidth))
  }

  /** Keeps the keyboard width action consistent with mouse resizing, including mixed and multi-column selections. */
  fun constrainSelectedColumnWidthDelta(view: GridTable, selectedColumns: IntArray, delta: Int): Int {
    if (delta <= 0 || selectedColumns.isEmpty()) return delta
    val frozen = frozenView ?: return delta
    var selectedPinned = 0
    var selectedUnpinned = 0
    for (column in selectedColumns) {
      if (view === primaryView && column in 0 until primaryView.columnCount) {
        if (isPinnedPlaceholder(primaryView.columnModel, column)) selectedPinned++ else selectedUnpinned++
      }
      else if (view === frozen && column in 0 until frozen.columnCount) {
        selectedPinned++
      }
    }
    if (selectedPinned == 0) return delta

    val parent = layout.findScrollPane() ?: return delta
    val availableWidth = layout.availableColumnsWidth(parent)
    if (availableWidth <= 0) return delta
    val pinnedWidth = layout.frozenColumnsWidth(frozen)
    val unpinnedWidth = primaryView.columnModel.totalColumnWidth
    if (!PinnedColumnsFit.fits(pinnedWidth, unpinnedWidth, availableWidth)) return 0

    var low = 0
    var high = delta
    while (low < high) {
      val candidate = ((low.toLong() + high + 1) / 2).toInt()
      val candidatePinnedWidth = widthAfterDelta(pinnedWidth, selectedPinned, candidate)
      val candidateUnpinnedWidth = widthAfterDelta(unpinnedWidth, selectedUnpinned, candidate)
      if (PinnedColumnsFit.fits(candidatePinnedWidth, candidateUnpinnedWidth, availableWidth)) low = candidate
      else high = candidate - 1
    }
    return low
  }

  private fun widthAfterDelta(width: Int, columnCount: Int, delta: Int): Int =
    (width.toLong() + columnCount.toLong() * delta).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
