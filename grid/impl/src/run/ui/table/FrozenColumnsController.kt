package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.datagrid.SelectionModelUtil
import com.intellij.database.run.ui.TableResultPanel
import com.intellij.database.run.ui.grid.GridRowHeader
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ComponentUtil
import com.intellij.ui.JBAutoScroller
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.AbstractAction
import javax.swing.ActionMap
import javax.swing.DefaultListSelectionModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.event.MouseInputAdapter
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import javax.swing.table.TableColumnModel

/**
 * Owns the secondary table used to render frozen columns and coordinates interactions that cross the boundary
 * between it and the primary table. Rendering and ordinary table behavior remain in [TableResultView].
 */
internal class FrozenColumnsController(
  private val primaryView: TableResultView,
  private val resultPanel: DataGrid,
  private val columnHeaderPopupActions: ActionGroup,
  private val rowHeaderPopupActions: ActionGroup,
) : Disposable {
  private var frozenView: TableResultView? = null
  private var frozenRowHeader: GridRowHeader? = null
  private var frozenModelSync: TableModelListener? = null
  private var originalCorner: Component? = null
  private var originalLowerCorner: Component? = null
  private var lowerCornerComponent: JComponent? = null
  private var gutterWrappers: GutterWrappers? = null
  private var mirroringSelection = false
  private var resizingFrozenColumn = false
  private var frozenResizeCursorShown = false
  private var frozenResizeStartX = 0
  private var frozenResizeStartWidth = 0
  private var dragAnchorRow = -1
  private var dragAnchorColumn = -1
  private val componentMouseListeners = mutableListOf<MouseListener>()
  private var mainColumnMirror: ListSelectionListener? = null
  private var frozenColumnMirror: ListSelectionListener? = null
  private var frozenColumnOrderMirror: TableColumnModelListener? = null
  private var frozenMoveColumnListener: MoveColumnListener? = null
  private var showRowNumbers = false

  fun getPrimaryView(): TableResultView = primaryView

  fun getFrozenView(): TableResultView? = frozenView

  fun isCellComponent(component: Component?): Boolean = component === primaryView || component === frozenView

  fun isEditingInFrozenView(): Boolean = frozenView?.isEditing == true

  /** Installs navigation that treats the frozen strip and the primary table as one displayed column sequence. */
  fun installColumnNavigationActions(view: TableResultView, actionMap: ActionMap) {
    wrapColumnMoveAction(view, actionMap, "selectNextColumnCell", forward = true, extend = false)
    wrapColumnMoveAction(view, actionMap, "selectPreviousColumnCell", forward = false, extend = false)
    wrapColumnMoveAction(view, actionMap, "selectNextColumn", forward = true, extend = false)
    wrapColumnMoveAction(view, actionMap, "selectPreviousColumn", forward = false, extend = false)
    wrapColumnMoveAction(view, actionMap, "selectNextColumnExtendSelection", forward = true, extend = true)
    wrapColumnMoveAction(view, actionMap, "selectPreviousColumnExtendSelection", forward = false, extend = true)
    wrapColumnEdgeAction(view, actionMap, "selectFirstColumn", first = true, extend = false)
    wrapColumnEdgeAction(view, actionMap, "selectLastColumn", first = false, extend = false)
    wrapColumnEdgeAction(view, actionMap, "selectFirstColumnExtendSelection", first = true, extend = true)
    wrapColumnEdgeAction(view, actionMap, "selectLastColumnExtendSelection", first = false, extend = true)
  }

  private fun wrapColumnMoveAction(view: TableResultView, actionMap: ActionMap, name: String, forward: Boolean, extend: Boolean) {
    wrapNavigationAction(actionMap, name) { handleColumnMove(view, forward, extend) }
  }

  private fun wrapColumnEdgeAction(view: TableResultView, actionMap: ActionMap, name: String, first: Boolean, extend: Boolean) {
    wrapNavigationAction(actionMap, name) { handleColumnEdgeMove(view, first, extend) }
  }

  private fun wrapNavigationAction(actionMap: ActionMap, name: String, handler: () -> Boolean) {
    val original = actionMap[name] ?: return
    actionMap.put(name, object : AbstractAction() {
      override fun actionPerformed(event: ActionEvent) {
        if (!handler()) original.actionPerformed(event)
      }
    })
  }

  /**
   * Keeps a dragged column after the pinned ones, which sit here at zero width: dropping in front of them looks like a
   * drop on the leftmost column but reorders the columns behind the strip. Pinned columns further along are crossed
   * like any other, and reordering from code is left alone.
   */
  fun adjustColumnMoveTarget(view: TableResultView, targetIndex: Int): Int {
    if (view !== primaryView || frozenView == null || view.tableHeader.draggedColumn == null) return targetIndex
    val columns = view.columnModel
    var firstUnpinned = 0
    while (isPinnedPlaceholder(columns, firstUnpinned)) firstUnpinned++
    return maxOf(targetIndex, firstUnpinned)
  }

  private fun isPinnedPlaceholder(columns: TableColumnModel, index: Int): Boolean =
    index in 0 until columns.columnCount && (columns.getColumn(index) as? TableResultViewColumn)?.isFrozenHidden == true

  fun installColumnResizeHandle() {
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
          val width = maxOf(JBUI.scale(24), frozenResizeStartWidth + direction * (e.xOnScreen - frozenResizeStartX))
          last.setColumnWidthByUser(width)
          findScrollPane()?.let {
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
    primaryView.tableHeader.addMouseListener(handle)
    primaryView.tableHeader.addMouseMotionListener(handle)
  }

  private fun inFrozenResizeZone(x: Int): Boolean {
    if (frozenView == null || primaryView.isTransposed) return false
    val resizeArea = JBUI.scale(3)
    return if (primaryView.componentOrientation.isLeftToRight) x in 0..resizeArea
    else x in primaryView.tableHeader.width - resizeArea..primaryView.tableHeader.width
  }

  private fun lastFrozenColumn(): TableResultViewColumn? {
    val columns = frozenView?.columnModel ?: return null
    return if (columns.columnCount == 0) null else columns.getColumn(columns.columnCount - 1) as TableResultViewColumn
  }

  fun showRowNumbers(show: Boolean) {
    showRowNumbers = show
    val parent = findScrollPane() ?: return
    if (primaryView.isTransposed) {
      parent.setRowHeaderView(primaryView.createSizedRowHeader())
      return
    }
    if (frozenView != null) {
      installFrozenRegion(parent)
      parent.revalidate()
      parent.repaint()
      return
    }
    if (showRowNumbers) parent.setRowHeaderView(primaryView.createSizedRowHeader())
    else parent.setRowHeader(null)
  }

  fun refreshRowNumbers() {
    showRowNumbers(showRowNumbers)
  }

  /**
   * Shows the currently visible [pinnedModelIndices] in the strip, in the order the main table has them. They keep
   * their place there at width 0, so pinning never reorders anything: the order stays the user's own, and so does the
   * persisted one. Logical pins that are not present in the main view do not create an empty strip.
   */
  fun setFrozenColumns(pinnedModelIndices: Collection<Int>) {
    val parent = findScrollPane() ?: return
    if ((primaryView.isEditing || isEditingInFrozenView()) && !primaryView.stopEditing()) primaryView.cancelEditing()
    restorePreviouslyFrozenColumns()

    val pinned = pinnedModelIndices.toSet()
    if (pinned.isEmpty() || primaryView.isTransposed || !TableResultPanel.isColumnPinningEnabled()) {
      removeFrozenView(parent)
      return
    }

    val mainColumns = primaryView.columnModel
    val visiblePinnedColumns = mutableListOf<TableResultViewColumn>()
    for (viewIndex in 0 until mainColumns.columnCount) {
      val mainColumn = mainColumns.getColumn(viewIndex) as TableResultViewColumn
      if (mainColumn.modelIndex in pinned) visiblePinnedColumns.add(mainColumn)
    }
    if (visiblePinnedColumns.isEmpty()) {
      removeFrozenView(parent)
      return
    }

    val frozen = frozenView ?: createFrozenView(parent)
    val orientation = parent.componentOrientation
    primaryView.applyComponentOrientation(orientation)
    frozen.applyComponentOrientation(orientation)

    (frozen.columnModel as TableResultView.MyTableColumnModel).removeAllColumns()
    for (mainColumn in visiblePinnedColumns) {
      val frozenColumn = frozen.columnCache.getOrCreateColumn(mainColumn.modelIndex)
      val width = if (mainColumn.isWidthSetByUser) mainColumn.columnWidth else maxOf(mainColumn.width, mainColumn.columnWidth)
      frozenColumn.setFrozenColumnWidth(width, mainColumn.isWidthSetByUser)
      frozenColumn.width = width
      frozen.columnModel.addColumn(frozenColumn)
      hideMainColumn(mainColumn)
    }

    primaryView.syncAppearanceToFrozenView(frozen)
    installFrozenRegion(parent)
    mirrorColumnSelection(primaryView.columnModel.selectionModel, frozen.columnModel.selectionModel, false)
    frozen.updateSortKeysFromColumnAttributes()
    parent.revalidate()
    parent.repaint()
  }

  private fun createFrozenView(parent: TableScrollPane): TableResultView {
    originalCorner = parent.getCorner(ScrollPaneConstants.UPPER_LEADING_CORNER)
    originalLowerCorner = parent.getCorner(ScrollPaneConstants.LOWER_LEADING_CORNER)
    val frozen = TableResultView(resultPanel, columnHeaderPopupActions, rowHeaderPopupActions, this)
    frozenView = frozen
    frozen.autoResizeMode = TableResultView.AUTO_RESIZE_OFF
    frozen.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
    frozen.cellSelectionEnabled = true
    // Rows share one model; columns use separate models mirrored below so selection paints correctly in both tables.
    frozen.selectionModel = primaryView.selectionModel
    TableSelectionModel.install(frozen, resultPanel)
    installRightButtonCellSelect(frozen)
    componentMouseListeners.forEach(frozen::addMouseListener)

    val mainColumns = primaryView.columnModel.selectionModel
    val frozenColumns = frozen.columnModel.selectionModel
    val mirror = ListSelectionListener { mirrorColumnSelection(mainColumns, frozenColumns, false) }
    mainColumnMirror = mirror
    mainColumns.addListSelectionListener(mirror)
    val frozenMirror = ListSelectionListener { mirrorColumnSelection(frozenColumns, mainColumns, true) }
    frozenColumnMirror = frozenMirror
    frozenColumns.addListSelectionListener(frozenMirror)
    installFrozenColumnOrderMirror(frozen)
    installFrozenMoveColumnListener(frozen)

    // The frozen table has its own model instance, so forward granular updates without stacking listeners on rebuild.
    val modelSync = TableModelListener { event ->
      val currentFrozen = frozenView ?: return@TableModelListener
      val model = currentFrozen.model
      model.fireTableChanged(TableModelEvent(model, event.firstRow, event.lastRow, event.column, event.type))
    }
    frozenModelSync = modelSync
    primaryView.model.addTableModelListener(modelSync)
    return frozen
  }

  private fun removeFrozenView(parent: TableScrollPane) {
    if (frozenView == null) return
    disposeFrozenView()
    parent.setCorner(ScrollPaneConstants.UPPER_LEADING_CORNER, originalCorner)
    parent.setCorner(ScrollPaneConstants.LOWER_LEADING_CORNER, originalLowerCorner)
    showRowNumbers(showRowNumbers)
    parent.revalidate()
    parent.repaint()
  }

  private fun disposeFrozenView() {
    val frozen = frozenView ?: return
    frozenView = null
    frozenModelSync?.let(primaryView.model::removeTableModelListener)
    frozenModelSync = null
    mainColumnMirror?.let(primaryView.columnModel.selectionModel::removeListSelectionListener)
    mainColumnMirror = null
    frozenColumnMirror?.let(frozen.columnModel.selectionModel::removeListSelectionListener)
    frozenColumnMirror = null
    frozenColumnOrderMirror?.let(frozen.columnModel::removeColumnModelListener)
    frozenColumnOrderMirror = null
    frozenMoveColumnListener?.let {
      frozen.tableHeader.removeMouseListener(it)
      frozen.columnModel.removeColumnModelListener(it)
    }
    frozenMoveColumnListener = null
    componentMouseListeners.forEach(frozen::removeMouseListener)
    frozenRowHeader = null
    gutterWrappers = null
    // JTable and TableExpandableItemsHandler both listen to the row selection model. Replacing the shared model lets
    // each unregister from the primary view before the strip becomes otherwise unreachable.
    frozen.selectionModel = DefaultListSelectionModel()
    Disposer.dispose(frozen)
  }

  override fun dispose() {
    val parent = if (frozenView == null) null else findScrollPane()
    disposeFrozenView()
    // Hand the corner slots back rather than leaving components around a disposed frozen view installed.
    parent?.setCorner(ScrollPaneConstants.UPPER_LEADING_CORNER, originalCorner)
    parent?.setCorner(ScrollPaneConstants.LOWER_LEADING_CORNER, originalLowerCorner)
    componentMouseListeners.clear()
    originalCorner = null
    originalLowerCorner = null
  }

  private fun restorePreviouslyFrozenColumns() {
    val frozenColumns = frozenView?.columnModel ?: return
    for (index in 0 until frozenColumns.columnCount) {
      val frozenColumn = frozenColumns.getColumn(index) as TableResultViewColumn
      // Go by column, not by its place in the view: a column hidden meanwhile is not shown but still holds its width,
      // and left at the zero width of the strip it would come back at the minimum one. A column dropped from the data
      // has nothing left to restore.
      val cache = primaryView.columnCache
      if (!cache.hasCachedColumn(frozenColumn.modelIndex)) continue
      restoreMainColumn(cache.getOrCreateColumn(frozenColumn.modelIndex), frozenColumn)
    }
  }

  private fun installFrozenColumnOrderMirror(frozen: TableResultView) {
    val listener = object : TableColumnModelListener {
      override fun columnMoved(event: TableColumnModelEvent) {
        if (event.fromIndex == event.toIndex) return
        val target = mainTargetForStripMove(frozen, event.toIndex) ?: return
        val moved = primaryView.viewColumnOf(frozen.columnModel.getColumn(event.toIndex).modelIndex)
        if (moved < 0 || moved == target) return
        resultPanel.runWithIgnoreSelectionChanges { primaryView.moveColumn(moved, target) }
      }

      override fun columnAdded(event: TableColumnModelEvent) = Unit
      override fun columnRemoved(event: TableColumnModelEvent) = Unit
      override fun columnMarginChanged(event: ChangeEvent) = Unit
      override fun columnSelectionChanged(event: ListSelectionEvent) = Unit
    }
    frozenColumnOrderMirror = listener
    frozen.columnModel.addColumnModelListener(listener)
  }

  /** Where the column moved to [stripIndex] goes in the main table: next to the strip neighbour it ended up with. */
  private fun mainTargetForStripMove(frozen: TableResultView, stripIndex: Int): Int? {
    if (stripIndex < 0 || stripIndex >= frozen.columnCount) return null
    val moved = primaryView.viewColumnOf(frozen.columnModel.getColumn(stripIndex).modelIndex)
    if (moved < 0) return null
    val left = if (stripIndex > 0) primaryView.viewColumnOf(frozen.columnModel.getColumn(stripIndex - 1).modelIndex) else -1
    if (left >= 0) return if (moved < left) left else left + 1
    val right = if (stripIndex + 1 < frozen.columnCount) primaryView.viewColumnOf(frozen.columnModel.getColumn(stripIndex + 1).modelIndex) else -1
    if (right < 0) return null
    return if (moved > right) right else right - 1
  }

  private fun installFrozenMoveColumnListener(frozen: TableResultView) {
    // Mutable document grids update their source from MoveColumnListener on mouse release. The frozen strip mirrors
    // the Swing order into the primary table first, then this listener applies that primary order to the source.
    val listener = MoveColumnListener(resultPanel, primaryView)
    frozenMoveColumnListener = listener
    frozen.tableHeader.addMouseListener(listener)
    frozen.columnModel.addColumnModelListener(listener)
  }

  private fun installFrozenRegion(parent: TableScrollPane) {
    val frozen = frozenView ?: return
    val gutter = if (showRowNumbers && !primaryView.isTransposed) getFrozenRowHeader() else null
    renderFrozenRegion(parent, frozen, gutter)
  }

  private fun renderFrozenRegion(parent: TableScrollPane, frozen: TableResultView, gutter: GridRowHeader?) {
    val orientation = parent.componentOrientation
    var rowHeader: JComponent = frozen
    var corner: JComponent = frozen.tableHeader
    if (gutter != null) {
      val wrappers = gutterWrappers(gutter, frozen)
      wrappers.rowHeader.applyComponentOrientation(orientation)
      wrappers.corner.applyComponentOrientation(orientation)
      rowHeader = wrappers.rowHeader
      corner = wrappers.corner
    }
    // A JTableHeader is not a child of its table, so it never receives the orientation applied to the frozen view.
    frozen.tableHeader.applyComponentOrientation(orientation)
    // Each swap relayouts the scroll pane, and a rebuild for a second pinned column reuses the same components.
    if (parent.rowHeader?.view !== rowHeader) parent.setRowHeaderView(rowHeader)
    if (parent.getCorner(ScrollPaneConstants.UPPER_LEADING_CORNER) !== corner) {
      parent.setCorner(ScrollPaneConstants.UPPER_LEADING_CORNER, corner)
    }
    // Without a leading lower corner the horizontal scrollbar leaves a band under the strip painted in the scrollbar
    // background, which reads as extra empty space once a column is widened.
    val lowerCorner = lowerCornerComponent ?: FrozenLowerCorner().also { lowerCornerComponent = it }
    if (parent.getCorner(ScrollPaneConstants.LOWER_LEADING_CORNER) !== lowerCorner) {
      parent.setCorner(ScrollPaneConstants.LOWER_LEADING_CORNER, lowerCorner)
    }
  }

  /** The row-number gutter and the strip share the row header, and their headers share the corner. */
  private class GutterWrappers(
    val gutter: GridRowHeader,
    val frozen: TableResultView,
    val gutterCorner: JPanel,
    val rowHeader: JPanel,
    val corner: JPanel,
  )

  /** Built once per gutter and strip, so a rebuild for another pinned column does not replace the whole row header. */
  private fun gutterWrappers(gutter: GridRowHeader, frozen: TableResultView): GutterWrappers {
    val wrappers = gutterWrappers?.takeIf { it.gutter === gutter && it.frozen === frozen }
                   ?: createGutterWrappers(gutter, frozen).also { gutterWrappers = it }
    // Without the gutter the strip and its header go into the scroll pane itself, which takes them out of these panels.
    pair(wrappers.rowHeader, gutter, frozen)
    pair(wrappers.corner, wrappers.gutterCorner, frozen.tableHeader)
    return wrappers
  }

  private fun createGutterWrappers(gutter: GridRowHeader, frozen: TableResultView): GutterWrappers {
    val gutterCorner = object : JPanel(BorderLayout()) {
      override fun getPreferredSize(): Dimension =
        Dimension(gutter.preferredSize.width, frozen.tableHeader.preferredSize.height)
    }
    originalCorner?.let { gutterCorner.add(it, BorderLayout.CENTER) }
    return GutterWrappers(gutter, frozen, gutterCorner, JPanel(BorderLayout()), JPanel(BorderLayout()))
  }

  private fun pair(panel: JPanel, lineStart: Component, center: Component) {
    if (panel.componentCount == 2 && panel.getComponent(0) === lineStart && panel.getComponent(1) === center) return
    panel.removeAll()
    panel.add(lineStart, BorderLayout.LINE_START)
    panel.add(center, BorderLayout.CENTER)
  }

  /**
   * Fills the band the horizontal scrollbar leaves beside the strip with the grid background.
   */
  private inner class FrozenLowerCorner : JPanel() {
    init {
      isOpaque = true
    }

    override fun getBackground(): Color? = primaryView.background
  }

  private fun getFrozenRowHeader(): GridRowHeader {
    val header = frozenRowHeader ?: primaryView.createSizedRowHeader().also { frozenRowHeader = it }
    header.updatePreferredSize()
    return header
  }

  private fun mirrorColumnSelection(from: ListSelectionModel, to: ListSelectionModel, toMain: Boolean) {
    val frozen = frozenView ?: return
    if (mirroringSelection) return
    mirroringSelection = true
    // Mirroring is derived, so it must not scroll: the autoscroller would follow the lead row of the selection.
    try {
      runWithAutoscrollLocked {
        // Map by model identity: a frozen-column reorder can reach one column model before the other.
        val sourceView = if (toMain) frozen else primaryView
        val targetView = if (toMain) primaryView else frozen
        val modelToTarget = targetView.rawIndexConverter.column2View()
        val sourceAnchor = from.anchorSelectionIndex
        val targetAnchor = if (sourceAnchor in 0 until sourceView.columnCount) {
          modelToTarget.applyAsInt(sourceView.columnModel.getColumn(sourceAnchor).modelIndex)
        }
        else -1
        val sourceLead = from.leadSelectionIndex
        val targetLead = if (sourceLead in 0 until sourceView.columnCount) {
          modelToTarget.applyAsInt(sourceView.columnModel.getColumn(sourceLead).modelIndex)
        }
        else -1
        to.valueIsAdjusting = true
        if (toMain) {
          for (index in 0 until frozen.columnCount) {
            val targetIndex = modelToTarget.applyAsInt(frozen.columnModel.getColumn(index).modelIndex)
            if (targetIndex >= 0) to.removeSelectionInterval(targetIndex, targetIndex)
          }
        }
        else to.clearSelection()
        var index = from.minSelectionIndex
        val lastSelected = minOf(from.maxSelectionIndex, sourceView.columnCount - 1)
        while (index in 0..lastSelected) {
          if (from.isSelectedIndex(index)) {
            val targetIndex = modelToTarget.applyAsInt(sourceView.columnModel.getColumn(index).modelIndex)
            if (targetIndex >= 0) to.addSelectionInterval(targetIndex, targetIndex)
          }
          index++
        }
        // Adding the lead once more moves Swing's lead without changing the selection; restore the mapped anchor after
        // that. The primary model remains the source of truth when its anchor is not representable in the strip.
        if (targetLead >= 0 && to.isSelectedIndex(targetLead)) to.addSelectionInterval(targetLead, targetLead)
        if (targetAnchor >= 0) to.anchorSelectionIndex = targetAnchor
        to.valueIsAdjusting = false
      }
    }
    finally {
      mirroringSelection = false
    }
  }

  fun afterChangeSelection(view: TableResultView, columnIndex: Int, toggle: Boolean, extend: Boolean) {
    val frozen = frozenView
    if (view !== frozen || toggle || extend || columnIndex < 0 || columnIndex >= frozen.columnCount) return
    // The strip holds a subset of the columns, so its index means nothing in the main table: go through the model.
    val mainColumn = primaryView.viewColumnOf(frozen.columnModel.getColumn(columnIndex).modelIndex)
    if (mainColumn < 0) return
    primaryView.columnModel.selectionModel.setSelectionInterval(mainColumn, mainColumn)
  }

  fun processMouseEvent(view: TableResultView, event: MouseEvent, defaultProcessor: Runnable) {
    // Selection in the fixed strip must not scroll the main viewport. Keep the lock for the full mouse gesture.
    if (view !== primaryView) {
      runWithAutoscrollLocked {
        defaultProcessor.run()
        if (event.id == MouseEvent.MOUSE_RELEASED) primaryView.columnModel.selectionModel.valueIsAdjusting = false
      }
    }
    else defaultProcessor.run()
    if (event.id == MouseEvent.MOUSE_PRESSED && SwingUtilities.isLeftMouseButton(event) && frozenView != null) {
      dragAnchorRow = clampedRowAt(view, event)
      dragAnchorColumn = unifiedMainColumn(view, event)
    }
  }

  fun processMouseMotionEvent(view: TableResultView, event: MouseEvent) {
    if (event.id == MouseEvent.MOUSE_DRAGGED && !event.isConsumed && SwingUtilities.isLeftMouseButton(event)) {
      extendDragAcrossFrozenRegion(view, event)
    }
  }

  private fun extendDragAcrossFrozenRegion(view: TableResultView, event: MouseEvent) {
    val frozen = frozenView ?: return
    if (dragAnchorColumn < 0 || dragAnchorRow < 0) return
    if (GridUtil.isIntervalModifierSet(event) || GridUtil.isExclusiveModifierSet(event)) return
    if (!isDragOverOtherRegion(view, event)) return
    val targetColumn = unifiedMainColumn(view, event)
    val targetRow = clampedRowAt(view, event)
    if (targetColumn < 0 || targetRow < 0) return
    // BasicTableUI cannot extend a drag into another JTable, so continue it through the unified primary selection.
    val selection = SelectionModelUtil.get<GridRow, GridColumn>(resultPanel, primaryView) as? TableSelectionModel ?: return
    if (view !== primaryView) primaryView.columnModel.selectionModel.valueIsAdjusting = true
    selection.setRowSelectionInterval(dragAnchorRow, targetRow)
    // A column interval runs in main view order, where a pinned column keeps the index it had before it moved into the
    // strip, so the columns the drag swept are the ones between the anchor and the target on screen.
    selectDisplayedColumnRange(displayedColumnOrder(frozen), targetColumn, add = false, anchorOverride = dragAnchorColumn)
  }

  private fun isDragOverOtherRegion(view: TableResultView, event: MouseEvent): Boolean {
    val frozen = frozenView ?: return false
    val overFrozen = SwingUtilities.convertPoint(view, event.point, frozen).x < frozen.width
    return (view !== primaryView) != overFrozen
  }

  private fun unifiedMainColumn(view: TableResultView, event: MouseEvent): Int {
    val frozen = frozenView ?: return -1
    val inFrozen = SwingUtilities.convertPoint(view, event.point, frozen)
    if (inFrozen.x < frozen.width) {
      if (frozen.width <= 0 || frozen.columnCount == 0) return -1
      var frozenColumn = frozen.columnAtPoint(Point(inFrozen.x.coerceIn(0, frozen.width - 1), 0))
      if (frozenColumn < 0) frozenColumn = frozen.columnCount - 1
      val modelIndex = frozen.columnModel.getColumn(frozenColumn).modelIndex
      return primaryView.rawIndexConverter.column2View().applyAsInt(modelIndex)
    }
    if (primaryView.width <= 0) return -1
    val inMain = SwingUtilities.convertPoint(view, event.point, primaryView)
    return primaryView.columnAtPoint(Point(inMain.x.coerceIn(0, primaryView.width - 1), 0))
  }

  private fun clampedRowAt(view: TableResultView, event: MouseEvent): Int {
    if (view.height <= 0) return -1
    return view.rowAtPoint(Point(0, event.y.coerceIn(0, view.height - 1)))
  }

  /** Handles horizontal moves while a strip exists, skipping hidden placeholders and retaining one unified anchor. */
  fun handleColumnMove(view: TableResultView, forward: Boolean, extend: Boolean): Boolean {
    val frozen = frozenView ?: return false
    if (view !== frozen && view !== primaryView) return false
    val lead = leadColumn(view)
    if (lead !in 0 until view.columnCount) return false
    val order = displayedColumnOrder(frozen)
    val current = if (view === primaryView) lead else order.primaryIndexOfFrozen(lead) ?: return false
    val target = order.adjacent(current, forward) ?: return false
    val row = leadRow(view)
    if (row < 0) return false
    val focusTarget = if (isPinnedPlaceholder(primaryView.columnModel, target)) frozen else primaryView
    return (extend || focusTarget !== view) && changeColumnSelection(order, row, target, extend, focusTarget)
  }

  /** Handles moves to the first or last displayed column, including their selection-extending variants. */
  fun handleColumnEdgeMove(view: TableResultView, first: Boolean, extend: Boolean): Boolean {
    val frozen = frozenView ?: return false
    if (view !== frozen && view !== primaryView) return false
    val order = displayedColumnOrder(frozen)
    val target = order.edge(first) ?: return false
    val row = leadRow(view)
    if (row < 0) return false
    val focusTarget = if (isPinnedPlaceholder(primaryView.columnModel, target)) frozen else primaryView
    return changeColumnSelection(order, row, target, extend, focusTarget)
  }

  /** Applies a Shift+click cell range in displayed order, even when its anchor is in the other table. */
  fun handleCellRangeSelection(view: TableResultView, row: Int, column: Int, add: Boolean): Boolean {
    val frozen = frozenView ?: return false
    if ((view !== frozen && view !== primaryView) || row !in 0 until view.rowCount) return false
    val order = displayedColumnOrder(frozen)
    val target = mainColumnOfView(order, view, column)
    if (target < 0) return false

    val rows = primaryView.selectionModel
    val rowAnchor = rows.anchorSelectionIndex.takeIf { it in 0 until primaryView.rowCount } ?: row
    if (add) rows.addSelectionInterval(rowAnchor, row)
    else rows.setSelectionInterval(rowAnchor, row)
    return selectDisplayedColumnRange(order, target, add)
  }

  /** Applies a whole-column Shift range from the unified anchor to a column in either table. */
  fun selectDisplayedColumnRange(view: TableResultView, column: Int, add: Boolean): Boolean {
    val frozen = frozenView ?: return false
    if (view !== frozen && view !== primaryView) return false
    val order = displayedColumnOrder(frozen)
    val target = mainColumnOfView(order, view, column)
    return target >= 0 && selectDisplayedColumnRange(order, target, add)
  }

  private fun changeColumnSelection(order: DisplayedColumnOrder,
                                    row: Int,
                                    target: Int,
                                    extend: Boolean,
                                    focusTarget: TableResultView): Boolean {
    if (extend) {
      if (!selectDisplayedColumnRange(order, target, false)) return false
      if (focusTarget === primaryView) primaryView.scrollRectToVisible(primaryView.getCellRect(row, target, true))
    }
    else if (focusTarget === primaryView) primaryView.changeSelection(row, target, false, false)
    else runWithAutoscrollLocked {
      // The selected placeholder is already represented in the fixed strip, so it must not move the main viewport.
      primaryView.changeSelection(row, target, false, false)
    }
    focusTarget.requestFocusInWindow()
    return true
  }

  /** Applies a Shift range in screen order: the frozen strip followed by the visible columns in the main table. */
  private fun selectDisplayedColumnRange(order: DisplayedColumnOrder,
                                         target: Int,
                                         add: Boolean,
                                         anchorOverride: Int? = null): Boolean {
    val frozen = frozenView ?: return false
    val selection = primaryView.columnModel.selectionModel
    val anchor = (anchorOverride ?: selection.anchorSelectionIndex).takeIf(order::contains) ?: target
    val range = order.range(anchor, target) ?: return false

    mirroringSelection = true
    try {
      // A drag keeps the selection adjusting until the mouse is released, so restore what the caller had rather than
      // announcing a settled selection mid-gesture, which scrolls the main view to the column under the pointer.
      val wasAdjusting = selection.valueIsAdjusting
      selection.valueIsAdjusting = true
      try {
        if (!add) selection.clearSelection()
        for (column in range) {
          selection.addSelectionInterval(column, column)
        }
        selection.addSelectionInterval(target, target)
        selection.anchorSelectionIndex = anchor
      }
      finally {
        selection.valueIsAdjusting = wasAdjusting
      }
    }
    finally {
      mirroringSelection = false
    }
    mirrorColumnSelection(selection, frozen.columnModel.selectionModel, false)
    return true
  }

  private fun mainColumnOfView(order: DisplayedColumnOrder, view: TableResultView, viewColumn: Int): Int {
    if (viewColumn !in 0 until view.columnCount) return -1
    val frozen = frozenView ?: return -1
    return if (view === primaryView) viewColumn
    else if (view === frozen) order.primaryIndexOfFrozen(viewColumn) ?: -1
    else -1
  }

  private fun displayedColumnOrder(frozen: TableResultView): DisplayedColumnOrder = DisplayedColumnOrder(
    primaryModelOrder = (0 until primaryView.columnCount).map { primaryView.columnModel.getColumn(it).modelIndex },
    frozenModelOrder = (0 until frozen.columnCount).map { frozen.columnModel.getColumn(it).modelIndex },
  )

  private fun leadColumn(view: TableResultView): Int = view.columnModel.selectionModel.leadSelectionIndex

  private fun leadRow(view: TableResultView): Int = view.selectionModel.leadSelectionIndex

  /** [JBAutoScroller.AutoscrollLocker] is not reentrant: an inner lock would otherwise release its outer caller. */
  private fun runWithAutoscrollLocked(action: () -> Unit) {
    val locker = resultPanel.autoscrollLocker
    if (locker.locked()) action()
    else locker.runWithLock { action() }
  }

  fun columnMarginChanged(view: TableResultView) {
    if (view !== frozenView) return
    findScrollPane()?.let {
      it.revalidate()
      it.repaint()
    }
  }

  fun syncRowHeight(view: TableResultView, row: Int, rowHeight: Int) {
    if (view !== primaryView && view !== frozenView) return
    val sibling = if (view === primaryView) frozenView else primaryView
    if (sibling != null && row < sibling.rowCount && sibling.getRowHeight(row) != rowHeight) {
      sibling.setRowHeight(row, rowHeight)
    }
  }

  fun addMouseListenerToComponents(listener: MouseListener) {
    componentMouseListeners.add(listener)
    frozenView?.addMouseListener(listener)
  }

  private fun installRightButtonCellSelect(view: TableResultView) {
    view.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(event: MouseEvent) {
        if (!SwingUtilities.isRightMouseButton(event)) return
        val row = view.rowAtPoint(event.point)
        val column = view.columnAtPoint(event.point)
        if (row < 0 || column < 0 || view.isCellSelected(row, column)) return
        val modelRow = view.rawIndexConverter.row2Model().applyAsInt(row)
        val modelColumn = view.rawIndexConverter.column2Model().applyAsInt(column)
        resultPanel.selectionModel.setSelection(
          ModelIndexSet.forRows(resultPanel, modelRow),
          ModelIndexSet.forColumns(resultPanel, modelColumn),
        )
      }
    })
  }

  private fun findScrollPane(): TableScrollPane? = ComponentUtil.getParentOfType(TableScrollPane::class.java, primaryView)

  private fun hideMainColumn(column: TableResultViewColumn) {
    column.isFrozenHidden = true
    column.minWidth = 0
    column.maxWidth = 0
    column.preferredWidth = 0
    column.width = 0
  }

  private fun restoreMainColumn(column: TableResultViewColumn, frozenColumn: TableResultViewColumn) {
    val width = frozenColumn.columnWidth
    column.isFrozenHidden = false
    column.maxWidth = Int.MAX_VALUE
    column.minWidth = 15
    if (frozenColumn.isWidthSetByUser) column.setColumnWidthByUser(width)
    else column.setColumnWidth(width)
    column.width = width
  }
}
