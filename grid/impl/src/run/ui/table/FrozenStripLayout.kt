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
 * Places one pinned table in the scroll pane's row header and corners, and scrolls it horizontally when it overflows. It lives as
 * long as that table: the controller creates it with the table and uninstalls it when the table goes.
 */
internal class FrozenStripLayout(
  private val mainTable: GridTable,
  private val frozen: GridTable,
  /** The main table's scroll pane, whose row header and corners the strip takes over. */
  val scrollPane: TableScrollPane,
) {
  private val originalCorner: Component? = scrollPane.getCorner(ScrollPaneConstants.UPPER_LEADING_CORNER)
  private val originalLowerCorner: Component? = scrollPane.getCorner(ScrollPaneConstants.LOWER_LEADING_CORNER)
  private var scrolling: FrozenScrolling? = null
  private var updatingFrozenViewport = false
  /** The width of the pinned columns when the strip was last laid out, so a width change can tell whether they grew. */
  private var laidOutPinnedWidth = Int.MAX_VALUE

  /** The view's row header while it shows row numbers beside the strip. */
  private var gutter: GridRowHeader? = null
  // These fill in lazily, the wrappers only while the gutter shows, and go away with the strip.
  private var gutterWrappers: GutterWrappers? = null
  private var lowerCorner: JComponent? = null

  /** Zooms the grid on a pinch over the pinned part, keeping the pinched row and the main table's left edge in place. */
  private val pinnedPartMagnificator = Magnificator { scale, at ->
    val anchor = Point(mainTable.visibleRect.x, at.y)
    val zoomed = ClientProperty.get(mainTable, Magnificator.CLIENT_PROPERTY_KEY)?.magnify(scale, anchor) ?: anchor
    // The zoom scrolls to the returned point minus the pointer, so adding at.x puts zoomed.x at the main table's left edge.
    Point(zoomed.x + at.x, zoomed.y)
  }

  init {
    installFrozenScrolling()
  }

  /** The strip's horizontal scroll bar, or null once [uninstallFrozenScrolling] has run. */
  val frozenScrollBar: JScrollBar? get() = scrolling?.scrollBar

  /** The viewport that shows the strip, or null once [uninstallFrozenScrolling] has run. */
  val frozenViewport: JViewport? get() = scrolling?.viewport

  fun restoreOriginalCorners() {
    scrollPane.setCorner(ScrollPaneConstants.UPPER_LEADING_CORNER, originalCorner)
    scrollPane.setCorner(ScrollPaneConstants.LOWER_LEADING_CORNER, originalLowerCorner)
  }

  /** Lays the strip out, with the row numbers of [rowHeader], the view's row header, beside it. */
  fun installFrozenRegion(rowHeader: GridRowHeader?) {
    useGutter(rowHeader)
    gutter?.updatePreferredSize()
    renderFrozenRegion(gutter)
  }

  /** Takes [rowHeader] as the gutter beside the strip while it shows row numbers, and lets go of the one it replaces. */
  private fun useGutter(rowHeader: GridRowHeader?) {
    val gutter = rowHeader?.takeIf { !mainTable.isTransposed }
    val replaced = this.gutter
    if (gutter === replaced) return
    replaced?.let(::releaseGutter)
    this.gutter = gutter
    scrolling?.let { gutter?.addMouseWheelListener(it.wheelListener) }
  }

  /** The view keeps its gutter, so the gutter lets go of the strip's wheel listener and of the panel that holds the strip. */
  private fun releaseGutter(gutter: GridRowHeader) {
    scrolling?.let { gutter.removeMouseWheelListener(it.wheelListener) }
    gutterWrappers?.let {
      it.rowHeader.remove(gutter)
      // The gutter's corner is sized by the gutter, and it holds the scroll pane's original corner, which this layout keeps.
      it.gutterCorner.removeAll()
    }
    gutterWrappers = null
  }

  private fun renderFrozenRegion(gutter: GridRowHeader?) {
    val orientation = scrollPane.componentOrientation
    val scrolling = scrolling ?: return
    val viewport = scrolling.viewport
    val headerViewport = scrolling.headerViewport
    val viewportWrapper = scrolling.viewportWrapper
    var rowHeader: JComponent = viewportWrapper
    var corner: JComponent = headerViewport
    if (gutter != null) {
      val wrappers = gutterWrappers(gutter, viewport, headerViewport)
      wrappers.rowHeader.applyComponentOrientation(orientation)
      wrappers.corner.applyComponentOrientation(orientation)
      rowHeader = wrappers.rowHeader
      corner = wrappers.corner
    }
    else {
      center(viewportWrapper, viewport)
    }
    // A JTableHeader is not a child of its table, so it never receives the orientation applied to the pinned table.
    frozen.tableHeader.applyComponentOrientation(orientation)
    viewport.applyComponentOrientation(orientation)
    headerViewport.applyComponentOrientation(orientation)
    scrolling.scrollBar.applyComponentOrientation(orientation)
    // A pinch takes the magnificator of the view of the viewport under the pointer; over the pinned part that is this row header.
    ClientProperty.put(rowHeader, Magnificator.CLIENT_PROPERTY_KEY, pinnedPartMagnificator)
    // Reuse installed components to avoid relayout and flicker when pinning another column.
    if (scrollPane.rowHeader?.view !== rowHeader) replaceRowHeaderView(scrollPane, rowHeader)
    if (scrollPane.getCorner(ScrollPaneConstants.UPPER_LEADING_CORNER) !== corner) {
      scrollPane.setCorner(ScrollPaneConstants.UPPER_LEADING_CORNER, corner)
    }
    // Match the grid background in the corner beside the horizontal scrollbar.
    val lowerCorner = this.lowerCorner ?: FrozenLowerCorner().also { this.lowerCorner = it }
    configureLowerCorner(lowerCorner, gutter)
    if (scrollPane.getCorner(ScrollPaneConstants.LOWER_LEADING_CORNER) !== lowerCorner) {
      scrollPane.setCorner(ScrollPaneConstants.LOWER_LEADING_CORNER, lowerCorner)
    }
    updateFrozenViewport()
  }

  /** The row-number gutter and the strip share the row header, and their headers share the corner. */
  private class GutterWrappers(
    val gutter: GridRowHeader,
    val frozenViewport: HorizontalViewport,
    val gutterCorner: JPanel,
    val rowHeader: JPanel,
    val corner: JPanel,
  )

  /** Built once per gutter, so a rebuild for another pinned column does not replace the whole row header. */
  private fun gutterWrappers(gutter: GridRowHeader,
                             viewport: HorizontalViewport,
                             headerViewport: HorizontalViewport): GutterWrappers {
    val wrappers = gutterWrappers?.takeIf { it.gutter === gutter && it.frozenViewport === viewport }
                   ?: createGutterWrappers(gutter, viewport).also { gutterWrappers = it }
    // Without the gutter the strip and its header go into the scroll pane itself, which takes them out of these panels.
    pair(wrappers.rowHeader, gutter, viewport)
    pair(wrappers.corner, wrappers.gutterCorner, headerViewport)
    return wrappers
  }

  private fun createGutterWrappers(gutter: GridRowHeader, viewport: HorizontalViewport): GutterWrappers {
    val gutterCorner = object : JPanel(BorderLayout()) {
      override fun getPreferredSize(): Dimension =
        Dimension(gutter.preferredSize.width, frozen.tableHeader.preferredSize.height)
    }
    originalCorner?.let { gutterCorner.add(it, BorderLayout.CENTER) }
    return GutterWrappers(gutter, viewport, gutterCorner, JPanel(BorderLayout()), JPanel(BorderLayout()))
  }

  private fun pair(panel: JPanel, lineStart: Component, center: Component) {
    if (panel.componentCount == 2 && panel.getComponent(0) === lineStart && panel.getComponent(1) === center) return
    panel.removeAll()
    panel.add(lineStart, BorderLayout.LINE_START)
    panel.add(center, BorderLayout.CENTER)
  }

  private fun center(panel: JPanel, component: Component) {
    if (panel.componentCount == 1 && panel.getComponent(0) === component) return
    panel.removeAll()
    panel.add(component, BorderLayout.CENTER)
  }

  /** Fills the corner beside the horizontal scrollbar with the grid background. */
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
   * Caps the strip's viewport using [PinnedColumnsFit], preserving column widths and scrolling overflow.
   *
   * The row header derives its width from the live gutter and strip sizes to avoid gaps after gutter resizing.
   */
  private fun updateFrozenViewport(anchorNewOverflowAtTrailingEdge: Boolean = false) {
    val scrolling = scrolling ?: return
    if (updatingFrozenViewport) return
    val viewport = scrolling.viewport
    val headerViewport = scrolling.headerViewport
    val scrollBar = scrolling.scrollBar
    val availableWidth = availableColumnsWidth()
    if (availableWidth <= 0) return

    val pinnedWidth = frozenColumnsWidth()
    laidOutPinnedWidth = pinnedWidth
    val unpinnedWidth = mainTable.columnModel.totalColumnWidth
    val allColumnsFit = pinnedWidth.toLong() + unpinnedWidth <= availableWidth
    val minimumFrozenWidth = minOf(JBUI.scale(24), maxOf(0, availableWidth - 1))
    val maximumPinnedWidth = maxOf(PinnedColumnsFit.maximumPinnedWidth(availableWidth), minimumFrozenWidth)
    val visibleWidth = minOf(pinnedWidth, if (allColumnsFit) availableWidth else maximumPinnedWidth)
    val overflow = pinnedWidth > visibleWidth

    var layoutChanged = false
    updatingFrozenViewport = true
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
      if (scrollPane.isFrozenHorizontalScrollBarVisible != overflow) {
        scrollPane.setFrozenHorizontalScrollBarVisible(overflow)
        layoutChanged = true
      }
      updateFrozenScrollRange(pinnedWidth, visibleWidth, anchorNewOverflowAtTrailingEdge)
      if (scrollBar.isVisible != overflow) {
        scrollBar.isVisible = overflow
        layoutChanged = true
      }
    }
    finally {
      updatingFrozenViewport = false
    }
    if (layoutChanged) scrollPane.revalidate()
    scrollPane.repaint()
  }

  /** The width of the pinned columns, without [except]. */
  fun frozenColumnsWidth(except: TableResultViewColumn? = null): Int {
    var width = 0L
    for (index in 0 until frozen.columnCount) {
      val column = frozen.columnModel.getColumn(index) as TableResultViewColumn
      if (column !== except) width += column.frozenStripWidth
    }
    return width.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
  }

  private fun visibleFrozenWidth(): Int {
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
    return if (width > 0) width else scrollPane.viewport.extentSize.width + visibleFrozenWidth()
  }

  private fun updateFrozenScrollRange(contentWidth: Int,
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

  private fun installFrozenScrolling() {
    val viewport = HorizontalViewport(frozen)
    val viewportWrapper = JPanel(BorderLayout()).apply { isOpaque = false }
    val headerViewport = HorizontalViewport(frozen.tableHeader)
    val scrollBar = JBScrollBar(JScrollBar.HORIZONTAL).apply {
      isVisible = false
      unitIncrement = JBUI.scale(16)
      accessibleContext.accessibleName = DataGridBundle.message("data.grid.pinned.columns.accessible.name")
      addAdjustmentListener { event ->
        if (updatingFrozenViewport) return@addAdjustmentListener
        updatingFrozenViewport = true
        try {
          viewport.viewPosition = Point(event.value, 0)
          headerViewport.viewPosition = Point(event.value, 0)
        }
        finally {
          updatingFrozenViewport = false
        }
      }
    }
    val viewportListener = ChangeListener {
      if (updatingFrozenViewport) return@ChangeListener
      updatingFrozenViewport = true
      try {
        val position = viewport.viewPosition.x
        headerViewport.viewPosition = Point(position, 0)
        scrollBar.value = position
      }
      finally {
        updatingFrozenViewport = false
      }
    }
    viewport.addChangeListener(viewportListener)
    val wheelListener = MouseWheelListener { event -> scrollFrozenHorizontally(event, scrollBar) }
    frozen.addMouseWheelListener(wheelListener)
    frozen.tableHeader.addMouseWheelListener(wheelListener)

    val mainListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        updateFrozenViewport()
      }
    }
    scrolling = FrozenScrolling(scrollPane, viewport, viewportWrapper, headerViewport, scrollBar,
                                scrollPane.horizontalScrollBarPolicy, viewportListener, wheelListener, mainListener)
    scrollPane.addComponentListener(mainListener)
    scrollPane.viewport.addComponentListener(mainListener)
  }

  /** Runs before the strip is dropped, since it reads the strip; the gutter parts go with the strip, the gutter stays with the view. */
  fun uninstallFrozenScrolling() {
    gutter?.let(::releaseGutter)
    gutter = null
    scrolling?.uninstall(frozen)
    scrolling = null
    updatingFrozenViewport = false
    laidOutPinnedWidth = Int.MAX_VALUE
    gutterWrappers = null
    lowerCorner = null
  }

  private fun scrollFrozenHorizontally(event: MouseWheelEvent, scrollBar: JScrollBar) {
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
    val pane = scrolling.parent
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
    val pane = scrolling?.parent ?: return
    pane.dispatchEvent(SwingUtilities.convertMouseEvent(source, event, pane))
  }

  /**
   * The horizontal scrolling of the strip: two viewports, a scroll bar, and the listeners that keep them in step.
   * All of it is installed at once and removed at once, so one field holds it and no caller can meet a half of it.
   */
  private class FrozenScrolling(
    val parent: TableScrollPane,
    val viewport: HorizontalViewport,
    val viewportWrapper: JPanel,
    val headerViewport: HorizontalViewport,
    val scrollBar: JScrollBar,
    val originalHorizontalScrollBarPolicy: Int,
    val viewportListener: ChangeListener,
    val wheelListener: MouseWheelListener,
    val mainViewportListener: ComponentListener,
  ) {
    val latching = LatchingScroll()

    fun uninstall(frozen: GridTable) {
      parent.removeComponentListener(mainViewportListener)
      parent.viewport.removeComponentListener(mainViewportListener)
      viewport.removeChangeListener(viewportListener)
      frozen.removeMouseWheelListener(wheelListener)
      frozen.tableHeader.removeMouseWheelListener(wheelListener)
      parent.rowHeader?.setPreferredSize(null)
      parent.setFrozenHorizontalScrollBarVisible(false)
      parent.horizontalScrollBarPolicy = originalHorizontalScrollBarPolicy
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

  /** Reveals [frozenColumn], the pinned table's column that keyboard navigation reached, in an overflowing strip. */
  fun scrollFrozenColumnToVisible(frozenColumn: Int) {
    val scrollBar = scrolling?.scrollBar?.takeIf { it.isVisible } ?: return
    if (frozenColumn !in 0 until frozen.columnCount) return
    val cell = frozen.getCellRect(0, frozenColumn, true)
    val position = when {
      cell.x < scrollBar.value -> cell.x
      cell.x + cell.width > scrollBar.value + scrollBar.visibleAmount -> maxOf(0, cell.x + cell.width - scrollBar.visibleAmount)
      else -> return
    }
    scrollBar.value = position
  }

  /** Lays the strip out again after a width change that [table] reports, once that table has settled the resized width. */
  fun columnMarginChanged(table: GridTable) {
    if (table !== frozen && table !== mainTable) return
    // Both tables hear of every width change: follow the edge of a pinned column that grew, otherwise keep the strip where it is.
    val anchorAtTrailingEdge = frozenColumnsWidth() > laidOutPinnedWidth
    updateFrozenViewport(anchorNewOverflowAtTrailingEdge = anchorAtTrailingEdge)
  }
}

/** The width the columns get in [parent], without the row-number [gutterWidth], the vertical scroll bar and the borders. */
internal fun containerColumnsWidth(parent: TableScrollPane, gutterWidth: Int): Int {
  val insets = parent.insets
  val viewportBorderInsets = parent.viewportBorder?.getBorderInsets(parent)
  val verticalScrollBarWidth = parent.verticalScrollBar
    .takeIf { it.isVisible }
    ?.let { maxOf(it.width, it.preferredSize.width) } ?: 0
  return parent.width - insets.left - insets.right -
         (viewportBorderInsets?.left ?: 0) - (viewportBorderInsets?.right ?: 0) -
         verticalScrollBarWidth - gutterWidth
}

/** The width of what [parent] shows in its row header, which is the row-number gutter while no strip is shown. */
internal fun shownRowHeaderWidth(parent: TableScrollPane): Int {
  val rowHeader = parent.rowHeader ?: return 0
  return rowHeader.extentSize.width.takeIf { it > 0 } ?: rowHeader.preferredSize.width
}
