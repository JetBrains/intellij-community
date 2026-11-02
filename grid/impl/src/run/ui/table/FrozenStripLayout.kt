package com.intellij.database.run.ui.table

import com.intellij.database.DataGridBundle
import com.intellij.database.run.ui.grid.GridRowHeader
import com.intellij.ui.ClientProperty
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.Magnificator
import com.intellij.ui.scroll.LatchingScroll
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Puts the pinned table in the main table's scroll pane as its row header, so both tables scroll vertically as one. The strip is the
 * pinned table's area beside the main table. The pinned header goes in the upper leading corner. When shown, row numbers appear beside the
 * pinned table.
 *
 * Unless every column fits, the strip leaves part of the width to the main table, so the divider stays in reach. [PinnedColumnsFit] sets
 * how much. Pinned columns that don't fit scroll horizontally, with a scroll bar of their own in the lower leading corner.
 *
 * One instance serves one pinned table.
 */
internal class FrozenStripLayout(
  private val mainTable: GridTable,
  private val pinnedTable: GridTable,
  /** The main table's scroll pane, whose row header and corners the strip takes over. */
  val scrollPane: TableScrollPane,
) {
  private val originalUpperCorner: Component? = scrollPane.getCorner(ScrollPaneConstants.UPPER_LEADING_CORNER)
  private val originalLowerCorner: Component? = scrollPane.getCorner(ScrollPaneConstants.LOWER_LEADING_CORNER)
  private var scrolling: FrozenScrolling? = null
  /**
   * True while this layout itself moves the pinned viewports or their scroll bar. The listeners that keep them in step, and a nested
   * update, then skip that move.
   */
  private var syncingPinnedScroll = false
  /** The width of the pinned columns when the strip was last laid out, so a width change can tell whether they grew. */
  private var laidOutPinnedWidth = Int.MAX_VALUE

  /** The view's row header beside the strip, or null while there is none or the grid is transposed. */
  private var gutter: GridRowHeader? = null
  // These fill in lazily, the wrappers only while the gutter shows, and go away with the strip.
  private var gutterWrappers: GutterWrappers? = null
  private var lowerCorner: JComponent? = null

  /** Zooms the grid on a pinch over the pinned table, keeping the pinched row and the main table's left edge in place. */
  private val pinnedTableMagnificator = Magnificator { scale, at ->
    val anchor = Point(mainTable.visibleRect.x, at.y)
    val zoomed = ClientProperty.get(mainTable, Magnificator.CLIENT_PROPERTY_KEY)?.magnify(scale, anchor) ?: anchor
    // The zoom scrolls to the returned point minus the pointer, so adding at.x puts zoomed.x at the main table's left edge.
    Point(zoomed.x + at.x, zoomed.y)
  }

  init {
    installPinnedScrolling()
  }

  /** The strip's horizontal scroll bar, or null once [uninstall] has run. */
  val pinnedScrollBar: JScrollBar? get() = scrolling?.scrollBar

  /** The viewport that shows the pinned table, or null once [uninstall] has run. */
  val pinnedViewport: JViewport? get() = scrolling?.viewport

  fun restoreOriginalCorners() {
    scrollPane.setCorner(ScrollPaneConstants.UPPER_LEADING_CORNER, originalUpperCorner)
    scrollPane.setCorner(ScrollPaneConstants.LOWER_LEADING_CORNER, originalLowerCorner)
  }

  /** Lays the strip out, with the row numbers of [rowHeader], the view's row header, beside it. */
  fun layOut(rowHeader: GridRowHeader?) {
    useGutter(rowHeader)
    gutter?.updatePreferredSize()
    placeInScrollPane(gutter)
  }

  /** Makes [rowHeader] the gutter beside the strip, or none while the grid is transposed, and releases the previous gutter. */
  private fun useGutter(rowHeader: GridRowHeader?) {
    val gutter = rowHeader?.takeIf { !mainTable.isTransposed }
    val replaced = this.gutter
    if (gutter === replaced) return
    replaced?.let(::releaseGutter)
    this.gutter = gutter
    scrolling?.let { gutter?.addMouseWheelListener(it.wheelListener) }
  }

  /**
   * Detaches [gutter] from the strip: takes the strip's wheel listener off it and removes it from the wrapper panel. The view keeps the
   * gutter and disposes it.
   */
  private fun releaseGutter(gutter: GridRowHeader) {
    scrolling?.let { gutter.removeMouseWheelListener(it.wheelListener) }
    gutterWrappers?.let {
      it.rowHeaderView.remove(gutter)
      // The gutter's corner is sized by the gutter, and it holds the scroll pane's original corner, which this layout keeps.
      it.gutterCorner.removeAll()
    }
    gutterWrappers = null
  }

  private fun placeInScrollPane(gutter: GridRowHeader?) {
    val orientation = scrollPane.componentOrientation
    val scrolling = scrolling ?: return
    val viewport = scrolling.viewport
    val headerViewport = scrolling.headerViewport
    val viewportWrapper = scrolling.viewportWrapper
    var rowHeaderView: JComponent = viewportWrapper
    var upperCorner: JComponent = headerViewport
    if (gutter != null) {
      val wrappers = gutterWrappers(gutter, viewport, headerViewport)
      wrappers.rowHeaderView.applyComponentOrientation(orientation)
      wrappers.upperCorner.applyComponentOrientation(orientation)
      rowHeaderView = wrappers.rowHeaderView
      upperCorner = wrappers.upperCorner
    }
    else {
      fillWith(viewportWrapper, viewport)
    }
    // A JTableHeader is not a child of its table, so it never receives the orientation applied to the pinned table.
    pinnedTable.tableHeader.applyComponentOrientation(orientation)
    viewport.applyComponentOrientation(orientation)
    headerViewport.applyComponentOrientation(orientation)
    scrolling.scrollBar.applyComponentOrientation(orientation)
    // A pinch takes the magnificator of the view of the viewport under the pointer; over the pinned table that is this row header.
    ClientProperty.put(rowHeaderView, Magnificator.CLIENT_PROPERTY_KEY, pinnedTableMagnificator)
    // Reuse installed components to avoid relayout and flicker when pinning another column.
    if (scrollPane.rowHeader?.view !== rowHeaderView) replaceRowHeaderView(scrollPane, rowHeaderView)
    if (scrollPane.getCorner(ScrollPaneConstants.UPPER_LEADING_CORNER) !== upperCorner) {
      scrollPane.setCorner(ScrollPaneConstants.UPPER_LEADING_CORNER, upperCorner)
    }
    // Match the grid background in the corner beside the horizontal scrollbar.
    val lowerCorner = this.lowerCorner ?: FrozenLowerCorner().also { this.lowerCorner = it }
    configureLowerCorner(lowerCorner, gutter)
    if (scrollPane.getCorner(ScrollPaneConstants.LOWER_LEADING_CORNER) !== lowerCorner) {
      scrollPane.setCorner(ScrollPaneConstants.LOWER_LEADING_CORNER, lowerCorner)
    }
    updatePinnedViewport()
  }

  /**
   * The panels that put the gutter beside the strip: [rowHeaderView] holds the gutter and the strip's viewport, [upperCorner] holds
   * [gutterCorner] and the strip's header.
   */
  private class GutterWrappers(
    val gutter: GridRowHeader,
    val pinnedViewport: HorizontalViewport,
    val gutterCorner: JPanel,
    val rowHeaderView: JPanel,
    val upperCorner: JPanel,
  )

  /** Built once per gutter, so a rebuild for another pinned column does not replace the whole row header. */
  private fun gutterWrappers(gutter: GridRowHeader,
                             viewport: HorizontalViewport,
                             headerViewport: HorizontalViewport): GutterWrappers {
    val wrappers = gutterWrappers?.takeIf { it.gutter === gutter && it.pinnedViewport === viewport }
                   ?: createGutterWrappers(gutter, viewport).also { gutterWrappers = it }
    // Without the gutter the strip and its header go into the scroll pane itself, which takes them out of these panels.
    fillSideBySide(wrappers.rowHeaderView, gutter, viewport)
    fillSideBySide(wrappers.upperCorner, wrappers.gutterCorner, headerViewport)
    return wrappers
  }

  private fun createGutterWrappers(gutter: GridRowHeader, viewport: HorizontalViewport): GutterWrappers {
    val gutterCorner = object : JPanel(BorderLayout()) {
      override fun getPreferredSize(): Dimension =
        Dimension(gutter.preferredSize.width, pinnedTable.tableHeader.preferredSize.height)
    }
    originalUpperCorner?.let { gutterCorner.add(it, BorderLayout.CENTER) }
    return GutterWrappers(gutter, viewport, gutterCorner, JPanel(BorderLayout()), JPanel(BorderLayout()))
  }

  /** Puts the components in [panel] unless they are there already, so a relayout doesn't flicker. */
  private fun fillSideBySide(panel: JPanel, lineStart: Component, center: Component) {
    if (panel.componentCount == 2 && panel.getComponent(0) === lineStart && panel.getComponent(1) === center) return
    panel.removeAll()
    panel.add(lineStart, BorderLayout.LINE_START)
    panel.add(center, BorderLayout.CENTER)
  }

  /** Puts [component] in [panel] unless it is there already, so a relayout doesn't flicker. */
  private fun fillWith(panel: JPanel, component: Component) {
    if (panel.componentCount == 1 && panel.getComponent(0) === component) return
    panel.removeAll()
    panel.add(component, BorderLayout.CENTER)
  }

  /** Hosts the strip's scroll bar on the grid background, in the scroll pane's lower leading corner. */
  private inner class FrozenLowerCorner : JPanel() {
    init {
      layout = BorderLayout()
      isOpaque = true
    }

    override fun getBackground(): Color? = mainTable.background
  }

  private fun configureLowerCorner(lowerCorner: JComponent, gutter: GridRowHeader?) {
    lowerCorner.removeAll()
    if (gutter != null) {
      val gutterFill = object : JPanel() {
        override fun getPreferredSize(): Dimension = Dimension(gutter.preferredSize.width, 0)
      }.apply {
        isOpaque = false
      }
      lowerCorner.add(gutterFill, BorderLayout.LINE_START)
    }
    scrolling?.let { lowerCorner.add(it.scrollBar, BorderLayout.CENTER) }
    lowerCorner.applyComponentOrientation(mainTable.componentOrientation)
  }

  /**
   * Sizes the pinned table's viewport, and shows the strip's scroll bar while the pinned columns don't fit. Column widths stay;
   * [PinnedColumnsFit] caps the viewport. [anchorNewOverflowAtTrailingEdge] scrolls a new overflow to its trailing edge, for a pinned
   * column that grew.
   */
  private fun updatePinnedViewport(anchorNewOverflowAtTrailingEdge: Boolean = false) {
    val scrolling = scrolling ?: return
    if (syncingPinnedScroll) return
    val viewport = scrolling.viewport
    val headerViewport = scrolling.headerViewport
    val scrollBar = scrolling.scrollBar
    val availableWidth = availableColumnsWidth()
    if (availableWidth <= 0) return

    val pinnedWidth = pinnedColumnsWidth()
    laidOutPinnedWidth = pinnedWidth
    val unpinnedWidth = mainTable.columnModel.totalColumnWidth
    val allColumnsFit = pinnedWidth.toLong() + unpinnedWidth <= availableWidth
    val minimumPinnedWidth = minOf(JBUI.scale(24), maxOf(0, availableWidth - 1))
    val maximumPinnedWidth = maxOf(PinnedColumnsFit.maximumPinnedWidth(availableWidth), minimumPinnedWidth)
    val visibleWidth = minOf(pinnedWidth, if (allColumnsFit) availableWidth else maximumPinnedWidth)
    val overflow = pinnedWidth > visibleWidth

    var layoutChanged = false
    syncingPinnedScroll = true
    try {
      if (viewport.preferredWidth != visibleWidth) {
        viewport.preferredWidth = visibleWidth
        layoutChanged = true
      }
      if (headerViewport.preferredWidth != visibleWidth) {
        headerViewport.preferredWidth = visibleWidth
        layoutChanged = true
      }
      val policy = if (overflow) ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
      else scrolling.originalHorizontalScrollBarPolicy
      if (scrollPane.horizontalScrollBarPolicy != policy) {
        scrollPane.horizontalScrollBarPolicy = policy
        layoutChanged = true
      }
      if (scrollPane.isPinnedScrollBarVisible != overflow) {
        scrollPane.setPinnedScrollBarVisible(overflow)
        layoutChanged = true
      }
      updatePinnedScrollRange(pinnedWidth, visibleWidth, anchorNewOverflowAtTrailingEdge)
      if (scrollBar.isVisible != overflow) {
        scrollBar.isVisible = overflow
        layoutChanged = true
      }
    }
    finally {
      syncingPinnedScroll = false
    }
    if (layoutChanged) scrollPane.revalidate()
    scrollPane.repaint()
  }

  /** The width of the pinned columns, without [except]. */
  fun pinnedColumnsWidth(except: TableResultViewColumn? = null): Int {
    var width = 0L
    for (index in 0 until pinnedTable.columnCount) {
      val column = pinnedTable.columnModel.getColumn(index) as TableResultViewColumn
      if (column !== except) width += column.pinnedWidth
    }
    return width.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
  }

  private fun visiblePinnedWidth(): Int {
    val rowHeaderWidth = scrollPane.rowHeader?.extentSize?.width ?: return 0
    val gutterWidth = rowNumberGutterWidth()
    return maxOf(0, rowHeaderWidth - minOf(gutterWidth, rowHeaderWidth))
  }

  private fun rowNumberGutterWidth(): Int {
    if (mainTable.isTransposed) return 0
    return gutter?.preferredSize?.width ?: 0
  }

  /** The width the columns of both tables share while the strip shows. */
  fun availableColumnsWidth(): Int {
    val width = containerColumnsWidth(scrollPane, rowNumberGutterWidth())
    return if (width > 0) width else scrollPane.viewport.extentSize.width + visiblePinnedWidth()
  }

  private fun updatePinnedScrollRange(contentWidth: Int,
                                      visibleWidth: Int,
                                      anchorNewOverflowAtTrailingEdge: Boolean) {
    val scrolling = scrolling ?: return
    val viewport = scrolling.viewport
    val headerViewport = scrolling.headerViewport
    val scrollBar = scrolling.scrollBar
    val oldMaximum = maxOf(0, scrollBar.maximum - scrollBar.visibleAmount)
    val current = viewport.viewPosition.x
    val wasOverflowing = scrollBar.isVisible
    val wasAtLeadingEdge = current == leadingScrollPosition(oldMaximum)
    val wasAtTrailingEdge = current == trailingScrollPosition(oldMaximum)
    val maximum = maxOf(0, contentWidth - visibleWidth)
    val position = when {
      !wasOverflowing && anchorNewOverflowAtTrailingEdge -> trailingScrollPosition(maximum)
      !wasOverflowing || wasAtLeadingEdge -> leadingScrollPosition(maximum)
      wasAtTrailingEdge -> trailingScrollPosition(maximum)
      else -> current.coerceIn(0, maximum)
    }
    viewport.viewPosition = Point(position, 0)
    headerViewport.viewPosition = Point(position, 0)
    scrollBar.setValues(position, visibleWidth, 0, maxOf(contentWidth, visibleWidth))
    scrollBar.blockIncrement = maxOf(scrollBar.unitIncrement, visibleWidth - scrollBar.unitIncrement)
  }

  private fun trailingScrollPosition(maximum: Int): Int =
    if (mainTable.componentOrientation.isLeftToRight) maximum else 0

  private fun leadingScrollPosition(maximum: Int): Int =
    if (mainTable.componentOrientation.isLeftToRight) 0 else maximum

  private fun installPinnedScrolling() {
    val viewport = HorizontalViewport(pinnedTable)
    val viewportWrapper = JPanel(BorderLayout()).apply { isOpaque = false }
    val headerViewport = HorizontalViewport(pinnedTable.tableHeader)
    val scrollBar = JBScrollBar(JScrollBar.HORIZONTAL).apply {
      isVisible = false
      unitIncrement = JBUI.scale(16)
      accessibleContext.accessibleName = DataGridBundle.message("data.grid.pinned.columns.accessible.name")
      addAdjustmentListener { event ->
        if (syncingPinnedScroll) return@addAdjustmentListener
        syncingPinnedScroll = true
        try {
          viewport.viewPosition = Point(event.value, 0)
          headerViewport.viewPosition = Point(event.value, 0)
        }
        finally {
          syncingPinnedScroll = false
        }
      }
    }
    val viewportListener = ChangeListener {
      if (syncingPinnedScroll) return@ChangeListener
      syncingPinnedScroll = true
      try {
        val position = viewport.viewPosition.x
        headerViewport.viewPosition = Point(position, 0)
        scrollBar.value = position
      }
      finally {
        syncingPinnedScroll = false
      }
    }
    viewport.addChangeListener(viewportListener)
    val wheelListener = MouseWheelListener { event -> scrollPinnedHorizontally(event, scrollBar) }
    pinnedTable.addMouseWheelListener(wheelListener)
    pinnedTable.tableHeader.addMouseWheelListener(wheelListener)

    val resizeListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        updatePinnedViewport()
      }
    }
    scrolling = FrozenScrolling(scrollPane, viewport, viewportWrapper, headerViewport, scrollBar,
                                scrollPane.horizontalScrollBarPolicy, viewportListener, wheelListener, resizeListener)
    scrollPane.addComponentListener(resizeListener)
    scrollPane.viewport.addComponentListener(resizeListener)
  }

  /**
   * Takes the strip's listeners off the scroll pane and the pinned table, restores the horizontal scroll bar, and releases the gutter.
   * Runs while the pinned table still exists, since it removes listeners from that table. The view keeps the gutter.
   */
  fun uninstall() {
    gutter?.let(::releaseGutter)
    gutter = null
    scrolling?.uninstall(pinnedTable)
    scrolling = null
    syncingPinnedScroll = false
    laidOutPinnedWidth = Int.MAX_VALUE
    gutterWrappers = null
    lowerCorner = null
  }

  private fun scrollPinnedHorizontally(event: MouseWheelEvent, scrollBar: JScrollBar) {
    if (scrollBar.isVisible && shouldIgnorePerpendicularWheelDrift(event)) {
      event.consume()
      return
    }
    if (!event.isShiftDown || !scrollBar.isVisible) {
      forwardWheelToScrollPane(event)
      return
    }
    val rotation = event.preciseWheelRotation
    if (rotation == 0.0) return

    val wheelDirection = if (rotation > 0) 1 else -1
    val scrollDirection = if (mainTable.componentOrientation.isLeftToRight) wheelDirection else -wheelDirection
    val increment = if (event.scrollType == MouseWheelEvent.WHEEL_BLOCK_SCROLL) {
      scrollBar.getBlockIncrement(scrollDirection)
    }
    else {
      scrollBar.getUnitIncrement(scrollDirection) * maxOf(1, event.scrollAmount)
    }
    val distance = maxOf(1, (abs(rotation) * increment).roundToInt())
    scrollBar.value += scrollDirection * distance
    event.consume()
  }

  /** Lets a predominantly horizontal trackpad gesture own the small vertical wheel events emitted with it. */
  private fun shouldIgnorePerpendicularWheelDrift(event: MouseWheelEvent): Boolean {
    if (!LatchingScroll.isEnabled()) return false
    val scrolling = scrolling ?: return false
    val pane = scrolling.scrollPane
    val paneEvent = MouseWheelEvent(
      pane,
      event.id,
      event.`when`,
      event.modifiersEx,
      event.x,
      event.y,
      event.xOnScreen,
      event.yOnScreen,
      event.clickCount,
      event.isPopupTrigger,
      event.scrollType,
      event.scrollAmount,
      event.wheelRotation,
      event.preciseWheelRotation,
    )
    return scrolling.latching.shouldBeIgnored(paneEvent)
  }

  /**
   * Forwards unhandled wheel events because installing a wheel listener disables AWT's ancestor forwarding.
   */
  private fun forwardWheelToScrollPane(event: MouseWheelEvent) {
    if (event.isConsumed) return
    val source = event.component ?: return
    val pane = scrolling?.scrollPane ?: return
    pane.dispatchEvent(SwingUtilities.convertMouseEvent(source, event, pane))
  }

  /**
   * The horizontal scrolling of the strip: two viewports, a scroll bar, and the listeners that keep them in step.
   * All of it is installed at once and removed at once, so one field holds it and no caller can meet a half of it.
   */
  private class FrozenScrolling(
    val scrollPane: TableScrollPane,
    val viewport: HorizontalViewport,
    val viewportWrapper: JPanel,
    val headerViewport: HorizontalViewport,
    val scrollBar: JScrollBar,
    val originalHorizontalScrollBarPolicy: Int,
    val viewportListener: ChangeListener,
    val wheelListener: MouseWheelListener,
    /** Lays the strip out again when the scroll pane or its viewport is resized. */
    val resizeListener: ComponentListener,
  ) {
    val latching = LatchingScroll()

    fun uninstall(pinnedTable: GridTable) {
      scrollPane.removeComponentListener(resizeListener)
      scrollPane.viewport.removeComponentListener(resizeListener)
      viewport.removeChangeListener(viewportListener)
      pinnedTable.removeMouseWheelListener(wheelListener)
      pinnedTable.tableHeader.removeMouseWheelListener(wheelListener)
      scrollPane.rowHeader?.setPreferredSize(null)
      scrollPane.setPinnedScrollBarVisible(false)
      scrollPane.horizontalScrollBarPolicy = originalHorizontalScrollBarPolicy
    }
  }

  /** A viewport whose width is controlled independently while its height continues to follow its view. */
  private class HorizontalViewport(component: JComponent) : JViewport() {
    var preferredWidth: Int? = null

    init {
      view = component
      // Repaint on scroll so blitting cannot copy the viewport-edge divider into the content.
      scrollMode = SIMPLE_SCROLL_MODE
    }

    override fun getPreferredSize(): Dimension =
      Dimension(preferredWidth ?: view.preferredSize.width, view.preferredSize.height)
  }

  /** Scrolls an overflowing strip so that the pinned table's column at [localIndex] shows. */
  fun scrollPinnedColumnToVisible(localIndex: Int) {
    val scrollBar = scrolling?.scrollBar?.takeIf { it.isVisible } ?: return
    if (localIndex !in 0 until pinnedTable.columnCount) return
    val cell = pinnedTable.getCellRect(0, localIndex, true)
    val position = when {
      cell.x < scrollBar.value -> cell.x
      cell.x + cell.width > scrollBar.value + scrollBar.visibleAmount -> maxOf(0, cell.x + cell.width - scrollBar.visibleAmount)
      else -> return
    }
    scrollBar.value = position
  }

  /** Lays the strip out again after a width change that [table] reports, once that table has settled the resized width. */
  fun columnMarginChanged(table: GridTable) {
    if (table !== pinnedTable && table !== mainTable) return
    // Both tables hear of every width change: follow the edge of a pinned column that grew, otherwise keep the strip where it is.
    val anchorAtTrailingEdge = pinnedColumnsWidth() > laidOutPinnedWidth
    updatePinnedViewport(anchorNewOverflowAtTrailingEdge = anchorAtTrailingEdge)
  }
}

/** The width the columns get in [scrollPane], without the row-number [gutterWidth], the vertical scroll bar and the borders. */
internal fun containerColumnsWidth(scrollPane: TableScrollPane, gutterWidth: Int): Int {
  val insets = scrollPane.insets
  val viewportBorderInsets = scrollPane.viewportBorder?.getBorderInsets(scrollPane)
  val verticalScrollBarWidth = scrollPane.verticalScrollBar
    .takeIf { it.isVisible }
    ?.let { maxOf(it.width, it.preferredSize.width) } ?: 0
  return scrollPane.width - insets.left - insets.right -
         (viewportBorderInsets?.left ?: 0) - (viewportBorderInsets?.right ?: 0) -
         verticalScrollBarWidth - gutterWidth
}

/** The width of what [scrollPane] shows in its row header, which is the row-number gutter while no strip is shown. */
internal fun shownRowHeaderWidth(scrollPane: TableScrollPane): Int {
  val rowHeader = scrollPane.rowHeader ?: return 0
  return rowHeader.extentSize.width.takeIf { it > 0 } ?: rowHeader.preferredSize.width
}
