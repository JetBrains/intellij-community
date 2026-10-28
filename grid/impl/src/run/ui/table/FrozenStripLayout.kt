package com.intellij.database.run.ui.table

import com.intellij.database.DataGridBundle
import com.intellij.database.run.ui.grid.GridRowHeader
import com.intellij.ui.ClientProperty
import com.intellij.ui.ComponentUtil
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

/** Places the pinned table in the scroll pane's row header and corners, and scrolls it horizontally when it overflows. */
internal class FrozenStripLayout(
  private val primaryView: GridTable,
  private val currentFrozenView: () -> GridTable?,
  private val rowNumbersShown: () -> Boolean,
) {
  private val frozenView: GridTable? get() = currentFrozenView()
  private val showRowNumbers: Boolean get() = rowNumbersShown()

  private var originalCorner: Component? = null
  private var originalLowerCorner: Component? = null
  private var scrolling: FrozenScrolling? = null
  private var updatingFrozenViewport = false

  // These fill in lazily, the two gutter ones only when row numbers show, and go away with the strip.
  private var rowHeader: GridRowHeader? = null
  private var gutterWrappers: GutterWrappers? = null
  private var lowerCorner: JComponent? = null

  /** Zooms the grid on a pinch over the pinned part, keeping the pinched row and the main table's left edge in place. */
  private val pinnedPartMagnificator = Magnificator { scale, at ->
    val anchor = Point(primaryView.visibleRect.x, at.y)
    val zoomed = ClientProperty.get(primaryView, Magnificator.CLIENT_PROPERTY_KEY)?.magnify(scale, anchor) ?: anchor
    // The zoom scrolls to the returned point minus the pointer, so adding at.x puts zoomed.x at the main table's left edge.
    Point(zoomed.x + at.x, zoomed.y)
  }

  /** The strip's horizontal scroll bar, or null while nothing is pinned. */
  val frozenScrollBar: JScrollBar? get() = scrolling?.scrollBar

  /** The viewport that shows the strip, or null while nothing is pinned. */
  val frozenViewport: JViewport? get() = scrolling?.viewport

  fun findScrollPane(): TableScrollPane? = ComponentUtil.getParentOfType(TableScrollPane::class.java, primaryView)

  fun rememberOriginalCorners(parent: TableScrollPane) {
    originalCorner = parent.getCorner(ScrollPaneConstants.UPPER_LEADING_CORNER)
    originalLowerCorner = parent.getCorner(ScrollPaneConstants.LOWER_LEADING_CORNER)
  }

  fun restoreOriginalCorners(parent: TableScrollPane) {
    parent.setCorner(ScrollPaneConstants.UPPER_LEADING_CORNER, originalCorner)
    parent.setCorner(ScrollPaneConstants.LOWER_LEADING_CORNER, originalLowerCorner)
  }

  fun forgetOriginalCorners() {
    originalCorner = null
    originalLowerCorner = null
  }

  fun installFrozenRegion(parent: TableScrollPane) {
    val frozen = frozenView ?: return
    val gutter = if (showRowNumbers && !primaryView.isTransposed) getFrozenRowHeader() else null
    renderFrozenRegion(parent, frozen, gutter)
  }

  private fun renderFrozenRegion(parent: TableScrollPane, frozen: GridTable, gutter: GridRowHeader?) {
    val orientation = parent.componentOrientation
    val scrolling = scrolling ?: return
    val viewport = scrolling.viewport
    val headerViewport = scrolling.headerViewport
    val viewportWrapper = scrolling.viewportWrapper
    var rowHeader: JComponent = viewportWrapper
    var corner: JComponent = headerViewport
    if (gutter != null) {
      val wrappers = gutterWrappers(gutter, frozen, viewport, headerViewport)
      wrappers.rowHeader.applyComponentOrientation(orientation)
      wrappers.corner.applyComponentOrientation(orientation)
      rowHeader = wrappers.rowHeader
      corner = wrappers.corner
    }
    else {
      center(viewportWrapper, viewport)
    }
    // A JTableHeader is not a child of its table, so it never receives the orientation applied to the frozen view.
    frozen.tableHeader.applyComponentOrientation(orientation)
    viewport.applyComponentOrientation(orientation)
    headerViewport.applyComponentOrientation(orientation)
    scrolling.scrollBar.applyComponentOrientation(orientation)
    // A pinch takes the magnificator of the view of the viewport under the pointer; over the pinned part that is this row header.
    ClientProperty.put(rowHeader, Magnificator.CLIENT_PROPERTY_KEY, pinnedPartMagnificator)
    // Reuse installed components to avoid relayout and flicker when pinning another column.
    if (parent.rowHeader?.view !== rowHeader) parent.setRowHeaderView(rowHeader)
    if (parent.getCorner(ScrollPaneConstants.UPPER_LEADING_CORNER) !== corner) {
      parent.setCorner(ScrollPaneConstants.UPPER_LEADING_CORNER, corner)
    }
    // Match the grid background in the corner beside the horizontal scrollbar.
    checkNotNull(frozenView) { "The region renders here, so it must exist" }
    val lowerCorner = this.lowerCorner ?: FrozenLowerCorner().also { this.lowerCorner = it }
    configureLowerCorner(lowerCorner, gutter)
    if (parent.getCorner(ScrollPaneConstants.LOWER_LEADING_CORNER) !== lowerCorner) {
      parent.setCorner(ScrollPaneConstants.LOWER_LEADING_CORNER, lowerCorner)
    }
    updateFrozenViewport(parent)
  }

  /** The row-number gutter and the strip share the row header, and their headers share the corner. */
  private class GutterWrappers(
    val gutter: GridRowHeader,
    val frozen: GridTable,
    val frozenViewport: HorizontalViewport,
    val gutterCorner: JPanel,
    val rowHeader: JPanel,
    val corner: JPanel,
  )

  /** Built once per gutter and strip, so a rebuild for another pinned column does not replace the whole row header. */
  private fun gutterWrappers(gutter: GridRowHeader,
                             frozen: GridTable,
                             viewport: HorizontalViewport,
                             headerViewport: HorizontalViewport): GutterWrappers {
    checkNotNull(frozenView) { "The gutter belongs to the strip, so the region must exist here" }
    val wrappers = gutterWrappers?.takeIf {
      it.gutter === gutter && it.frozen === frozen && it.frozenViewport === viewport
    } ?: createGutterWrappers(gutter, frozen, viewport).also { gutterWrappers = it }
    // Without the gutter the strip and its header go into the scroll pane itself, which takes them out of these panels.
    pair(wrappers.rowHeader, gutter, viewport)
    pair(wrappers.corner, wrappers.gutterCorner, headerViewport)
    return wrappers
  }

  private fun createGutterWrappers(gutter: GridRowHeader,
                                   frozen: GridTable,
                                   viewport: HorizontalViewport): GutterWrappers {
    val gutterCorner = object : JPanel(BorderLayout()) {
      override fun getPreferredSize(): Dimension =
        Dimension(gutter.preferredSize.width, frozen.tableHeader.preferredSize.height)
    }
    originalCorner?.let { gutterCorner.add(it, BorderLayout.CENTER) }
    return GutterWrappers(gutter, frozen, viewport, gutterCorner, JPanel(BorderLayout()), JPanel(BorderLayout()))
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

    override fun getBackground(): Color? = primaryView.background
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
    lowerCorner.applyComponentOrientation(primaryView.componentOrientation)
  }

  /**
   * Caps the strip's viewport using [PinnedColumnsFit], preserving column widths and scrolling overflow.
   *
   * The row header derives its width from the live gutter and strip sizes to avoid gaps after gutter resizing.
   */
  private fun updateFrozenViewport(parent: TableScrollPane, anchorNewOverflowAtTrailingEdge: Boolean = false) {
    val scrolling = scrolling ?: return
    if (updatingFrozenViewport || parent !== scrolling.parent) return
    val frozen = frozenView ?: return
    val viewport = scrolling.viewport
    val headerViewport = scrolling.headerViewport
    val scrollBar = scrolling.scrollBar
    val availableWidth = availableColumnsWidth(parent)
    if (availableWidth <= 0) return

    val pinnedWidth = frozenColumnsWidth(frozen)
    val unpinnedWidth = primaryView.columnModel.totalColumnWidth
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
      if (parent.horizontalScrollBarPolicy != policy) {
        parent.horizontalScrollBarPolicy = policy
        layoutChanged = true
      }
      if (parent.isFrozenHorizontalScrollBarVisible != overflow) {
        parent.setFrozenHorizontalScrollBarVisible(overflow)
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
    if (layoutChanged) parent.revalidate()
    parent.repaint()
  }

  fun frozenColumnsWidth(frozen: GridTable, except: TableResultViewColumn? = null): Int {
    var width = 0L
    for (index in 0 until frozen.columnCount) {
      val column = frozen.columnModel.getColumn(index) as TableResultViewColumn
      if (column !== except) width += column.frozenStripWidth
    }
    return width.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
  }

  private fun visibleFrozenWidth(parent: TableScrollPane): Int {
    val rowHeaderWidth = parent.rowHeader?.extentSize?.width ?: return 0
    val gutterWidth = rowNumberGutterWidth(parent)
    return maxOf(0, rowHeaderWidth - minOf(gutterWidth, rowHeaderWidth))
  }

  private fun rowNumberGutterWidth(parent: TableScrollPane): Int {
    if (!showRowNumbers || primaryView.isTransposed) return 0
    val frozenGutterWidth = rowHeader?.preferredSize?.width
    if (frozenGutterWidth != null) return frozenGutterWidth
    val rowHeader = parent.rowHeader ?: return 0
    return rowHeader.extentSize.width.takeIf { it > 0 } ?: rowHeader.preferredSize.width
  }

  fun availableColumnsWidth(parent: TableScrollPane): Int {
    val insets = parent.insets
    val viewportBorderInsets = parent.viewportBorder?.getBorderInsets(parent)
    val verticalScrollBarWidth = parent.verticalScrollBar
      .takeIf { it.isVisible }
      ?.let { maxOf(it.width, it.preferredSize.width) } ?: 0
    val containerWidth = parent.width - insets.left - insets.right -
                         (viewportBorderInsets?.left ?: 0) - (viewportBorderInsets?.right ?: 0) -
                         verticalScrollBarWidth - rowNumberGutterWidth(parent)
    if (containerWidth > 0) return containerWidth

    val frozenWidth = if (frozenView == null) 0 else visibleFrozenWidth(parent)
    return parent.viewport.extentSize.width + frozenWidth
  }

  fun getAvailableColumnsWidth(): Int =
    findScrollPane()?.let(::availableColumnsWidth) ?: maxOf(0, primaryView.width)

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
    if (primaryView.componentOrientation.isLeftToRight) maximum else 0

  private fun leadingScrollPosition(maximum: Int): Int =
    if (primaryView.componentOrientation.isLeftToRight) 0 else maximum

  fun installFrozenScrolling(parent: TableScrollPane, frozen: GridTable) {
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
        updateFrozenViewport(parent)
      }
    }
    scrolling = FrozenScrolling(parent, viewport, viewportWrapper, headerViewport, scrollBar,
                                parent.horizontalScrollBarPolicy, viewportListener, wheelListener, mainListener)
    parent.addComponentListener(mainListener)
    parent.viewport.addComponentListener(mainListener)
  }

  /** Runs before the strip is dropped, since it reads the strip; the gutter parts go away with the strip. */
  fun uninstallFrozenScrolling() {
    scrolling?.uninstall(frozenView, rowHeader)
    scrolling = null
    updatingFrozenViewport = false
    rowHeader = null
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
    val scrollDirection = if (primaryView.componentOrientation.isLeftToRight) wheelDirection else -wheelDirection
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

    fun uninstall(frozen: GridTable?, gutter: GridRowHeader?) {
      parent.removeComponentListener(mainViewportListener)
      parent.viewport.removeComponentListener(mainViewportListener)
      viewport.removeChangeListener(viewportListener)
      frozen?.removeMouseWheelListener(wheelListener)
      frozen?.tableHeader?.removeMouseWheelListener(wheelListener)
      gutter?.removeMouseWheelListener(wheelListener)
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

  private fun getFrozenRowHeader(): GridRowHeader {
    checkNotNull(frozenView) { "The gutter belongs to the strip, so the region must exist here" }
    val header = rowHeader ?: primaryView.createSizedRowHeader().also {
      scrolling?.let { s -> it.addMouseWheelListener(s.wheelListener) }
      rowHeader = it
    }
    header.updatePreferredSize()
    return header
  }

  /** Reveals the pinned column reached by keyboard navigation in an overflowing strip. */
  fun scrollFrozenColumnToVisible(primaryColumn: Int) {
    val frozen = frozenView ?: return
    val scrollBar = scrolling?.scrollBar?.takeIf { it.isVisible } ?: return
    if (primaryColumn !in 0 until primaryView.columnModel.columnCount) return
    val frozenColumn = frozenViewColumnOf(frozen, primaryView.columnModel.getColumn(primaryColumn).modelIndex)
    if (frozenColumn < 0) return
    val cell = frozen.getCellRect(0, frozenColumn, true)
    val position = when {
      cell.x < scrollBar.value -> cell.x
      cell.x + cell.width > scrollBar.value + scrollBar.visibleAmount -> maxOf(0, cell.x + cell.width - scrollBar.visibleAmount)
      else -> return
    }
    scrollBar.value = position
  }

  private fun frozenViewColumnOf(frozen: GridTable, modelColumn: Int): Int {
    for (index in 0 until frozen.columnModel.columnCount) {
      if (frozen.columnModel.getColumn(index).modelIndex == modelColumn) return index
    }
    return -1
  }

  fun columnMarginChanged(view: GridTable) {
    if (view !== frozenView && view !== primaryView) return
    // Follow a pinned column's resized edge; preserve the strip's scroll position when main columns resize.
    val anchorAtTrailingEdge = view === frozenView
    findScrollPane()?.let { updateFrozenViewport(it, anchorNewOverflowAtTrailingEdge = anchorAtTrailingEdge) }
  }
}
