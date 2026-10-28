package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.ModelIndex
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBAutoScroller
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.AbstractAction
import javax.swing.ActionMap
import javax.swing.DefaultListSelectionModel
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableColumnModel

/**
 * Owns the secondary table used to render frozen columns and coordinates interactions that cross the boundary
 * between it and the primary table. Rendering and ordinary table behavior remain in [GridTable], the placement and
 * scrolling of the strip in [FrozenStripLayout], and the resize handle on the main header in [FrozenColumnResize].
 */
internal class FrozenColumnsController(
  private val primaryView: GridTable,
  private val resultPanel: DataGrid,
  private val columnHeaderPopupActions: ActionGroup,
  private val rowHeaderPopupActions: ActionGroup,
  private val hostFactory: (GridTable) -> GridTableHost,
  /** Runs after the pinned table is dropped, so the view can let go of what it set up and cached for it. */
  private val pinnedTableDropped: (GridTable) -> Unit,
) : Disposable {
  private var region: FrozenRegion? = null
  private var mirroringSelection = false
  private var dragAnchorRow = -1
  private var dragAnchorColumn = -1
  private val componentMouseListeners = mutableListOf<MouseListener>()
  private var showRowNumbers = false
  private val layout = FrozenStripLayout(primaryView, { frozenView }, { showRowNumbers })
  private val resize = FrozenColumnResize(primaryView, { frozenView }, layout)

  /** The strip, or null while nothing is pinned. */
  val frozenView: GridTable? get() = region?.view

  /** Installs navigation that treats the frozen strip and the primary table as one displayed column sequence. */
  fun installColumnNavigationActions(view: GridTable, actionMap: ActionMap) {
    wrapColumnMoveAction(view, actionMap, "selectNextColumnCell", forward = true, extend = false, wrapsRows = true)
    wrapColumnMoveAction(view, actionMap, "selectPreviousColumnCell", forward = false, extend = false, wrapsRows = true)
    wrapColumnMoveAction(view, actionMap, "selectNextColumn", forward = true, extend = false)
    wrapColumnMoveAction(view, actionMap, "selectPreviousColumn", forward = false, extend = false)
    wrapColumnMoveAction(view, actionMap, "selectNextColumnExtendSelection", forward = true, extend = true)
    wrapColumnMoveAction(view, actionMap, "selectPreviousColumnExtendSelection", forward = false, extend = true)
    wrapColumnEdgeAction(view, actionMap, "selectFirstColumn", first = true, extend = false)
    wrapColumnEdgeAction(view, actionMap, "selectLastColumn", first = false, extend = false)
    wrapColumnEdgeAction(view, actionMap, "selectFirstColumnExtendSelection", first = true, extend = true)
    wrapColumnEdgeAction(view, actionMap, "selectLastColumnExtendSelection", first = false, extend = true)
  }

  private fun wrapColumnMoveAction(view: GridTable,
                                   actionMap: ActionMap,
                                   name: String,
                                   forward: Boolean,
                                   extend: Boolean,
                                   wrapsRows: Boolean = false) {
    wrapNavigationAction(actionMap, name) { handleColumnMove(view, forward, extend, wrapsRows) }
  }

  private fun wrapColumnEdgeAction(view: GridTable, actionMap: ActionMap, name: String, first: Boolean, extend: Boolean) {
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
   * Prevents header drags from moving ahead of leading pinned placeholders.
   * Placeholders elsewhere remain crossable; programmatic moves are unrestricted.
   */
  fun adjustColumnMoveTarget(targetIndex: Int): Int {
    if (frozenView == null || primaryView.tableHeader.draggedColumn == null) return targetIndex
    val columns = primaryView.columnModel
    var firstUnpinned = 0
    while (isPinnedPlaceholder(columns, firstUnpinned)) firstUnpinned++
    return maxOf(targetIndex, firstUnpinned)
  }

  fun installColumnResizeHandle() = resize.installColumnResizeHandle()

  /** A direct resize may shrink freely, but it may not introduce or increase avoidable pinned-strip overflow. */
  fun constrainFrozenColumnResize(view: GridTable, column: TableResultViewColumn, requestedWidth: Int): Int =
    resize.constrainFrozenColumnResize(view, column, requestedWidth)

  /** Keeps the keyboard width action consistent with mouse resizing, including mixed and multi-column selections. */
  fun constrainSelectedColumnWidthDelta(view: GridTable, selectedColumns: IntArray, delta: Int): Int =
    resize.constrainSelectedColumnWidthDelta(view, selectedColumns, delta)

  fun getAvailableColumnsWidth(): Int = layout.getAvailableColumnsWidth()

  fun columnMarginChanged(view: GridTable) = layout.columnMarginChanged(view)

  fun showRowNumbers(show: Boolean) {
    showRowNumbers = show
    val parent = findScrollPane() ?: return
    if (primaryView.isTransposed) {
      parent.setRowHeaderView(primaryView.createSizedRowHeader())
      return
    }
    if (frozenView != null) {
      layout.installFrozenRegion(parent)
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
   * Renders the visible columns of [pinnedColumns] in main-table order, retaining zero-width placeholders there.
   * Hidden pins remain in the logical state but occupy no space.
   */
  fun setFrozenColumns(pinnedColumns: Set<ModelIndex<GridColumn>>) {
    val parent = findScrollPane() ?: return
    restorePreviouslyFrozenColumns()

    // The column model speaks in raw model indices, so unwrap once here instead of per column below.
    val pinned = pinnedColumns.mapTo(HashSet()) { it.value }
    if (pinned.isEmpty() || primaryView.isTransposed) {
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

    (frozen.columnModel as GridTable.MyTableColumnModel).removeAllColumns()
    for (mainColumn in visiblePinnedColumns) {
      val frozenColumn = frozen.columnCache.getOrCreateColumn(mainColumn.modelIndex)
      val width = mainColumn.frozenStripWidth
      frozenColumn.setFrozenColumnWidth(width, mainColumn.isWidthSetByUser)
      frozenColumn.width = width
      frozen.columnModel.addColumn(frozenColumn)
      hideMainColumn(mainColumn)
    }

    frozen.applyAppearance(primaryView.captureAppearance())
    layout.installFrozenRegion(parent)
    mirrorColumnSelection(primaryView.columnModel.selectionModel, frozen.columnModel.selectionModel, false)
    parent.revalidate()
    parent.repaint()
  }

  private fun createFrozenView(parent: TableScrollPane): GridTable {
    layout.rememberOriginalCorners(parent)
    val frozen = FrozenStripTable(resultPanel, primaryView.model, columnHeaderPopupActions, rowHeaderPopupActions)
      .also { it.installHost(hostFactory(it)) }
    // The tables share the data model, the row order and the row selection; columns use separate models, mirrored below, so
    // that selection paints correctly in both tables.
    frozen.selectionModel = primaryView.selectionModel
    TableSelectionModel.install(frozen, resultPanel)
    componentMouseListeners.forEach(frozen::addMouseListener)

    val mainColumns = primaryView.columnModel.selectionModel
    val frozenColumns = frozen.columnModel.selectionModel
    val mirror = ListSelectionListener { mirrorColumnSelection(mainColumns, frozenColumns, false) }
    mainColumns.addListSelectionListener(mirror)
    val frozenMirror = ListSelectionListener { mirrorColumnSelection(frozenColumns, mainColumns, true) }
    frozenColumns.addListSelectionListener(frozenMirror)

    val columnOrderMirror = createFrozenColumnOrderMirror(frozen)
    val moveColumnListener = createFrozenMoveColumnListener(frozen)
    layout.installFrozenScrolling(parent, frozen)
    region = FrozenRegion(frozen, mirror, frozenMirror, columnOrderMirror, moveColumnListener)
    return frozen
  }

  private fun removeFrozenView(parent: TableScrollPane) {
    if (frozenView == null) return
    disposeFrozenView()
    layout.restoreOriginalCorners(parent)
    showRowNumbers(showRowNumbers)
    parent.revalidate()
    parent.repaint()
  }

  private fun disposeFrozenView() {
    val region = region ?: return
    layout.uninstallFrozenScrolling()
    this.region = null
    region.dispose(primaryView, componentMouseListeners)
    pinnedTableDropped(region.view)
  }

  override fun dispose() {
    val parent = if (frozenView == null) null else findScrollPane()
    disposeFrozenView()
    resize.uninstallColumnResizeHandle()
    // Restore the original corners when disposing the strip.
    parent?.let(layout::restoreOriginalCorners)
    componentMouseListeners.clear()
    layout.forgetOriginalCorners()
  }

  private fun restorePreviouslyFrozenColumns() {
    val frozenColumns = frozenView?.columnModel ?: return
    for (index in 0 until frozenColumns.columnCount) {
      val frozenColumn = frozenColumns.getColumn(index) as TableResultViewColumn
      // Restore cached hidden columns too; removed columns no longer have a width to restore.
      val cache = primaryView.columnCache
      if (!cache.hasCachedColumn(frozenColumn.modelIndex)) continue
      restoreMainColumn(cache.getOrCreateColumn(frozenColumn.modelIndex), frozenColumn)
    }
  }

  private fun createFrozenColumnOrderMirror(frozen: GridTable): TableColumnModelListener {
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
    frozen.columnModel.addColumnModelListener(listener)
    return listener
  }

  /** Where the column moved to [stripIndex] goes in the main table: next to the strip neighbour it ended up with. */
  private fun mainTargetForStripMove(frozen: GridTable, stripIndex: Int): Int? {
    if (stripIndex < 0 || stripIndex >= frozen.columnCount) return null
    val moved = primaryView.viewColumnOf(frozen.columnModel.getColumn(stripIndex).modelIndex)
    if (moved < 0) return null
    val left = if (stripIndex > 0) primaryView.viewColumnOf(frozen.columnModel.getColumn(stripIndex - 1).modelIndex) else -1
    if (left >= 0) return if (moved < left) left else left + 1
    val right = if (stripIndex + 1 < frozen.columnCount) primaryView.viewColumnOf(frozen.columnModel.getColumn(stripIndex + 1).modelIndex) else -1
    if (right < 0) return null
    return if (moved > right) right else right - 1
  }

  private fun createFrozenMoveColumnListener(frozen: GridTable): MoveColumnListener {
    // Mutable document grids update their source from MoveColumnListener on mouse release. The frozen strip mirrors
    // the Swing order into the primary table first, then this listener applies that primary order to the source.
    val listener = MoveColumnListener(resultPanel, primaryView)
    frozen.tableHeader.addMouseListener(listener)
    frozen.columnModel.addColumnModelListener(listener)
    return listener
  }

  /**
   * The strip and the listeners that tie it to the primary table. All of it is created at once and disposed at once,
   * so one field holds it.
   */
  private class FrozenRegion(
    val view: GridTable,
    val mainColumnMirror: ListSelectionListener,
    val columnMirror: ListSelectionListener,
    val columnOrderMirror: TableColumnModelListener,
    val moveColumnListener: MoveColumnListener,
  ) {
    fun dispose(primary: GridTable, sharedMouseListeners: List<MouseListener>) {
      primary.columnModel.selectionModel.removeListSelectionListener(mainColumnMirror)
      view.columnModel.selectionModel.removeListSelectionListener(columnMirror)
      view.columnModel.removeColumnModelListener(columnOrderMirror)
      view.tableHeader.removeMouseListener(moveColumnListener)
      view.columnModel.removeColumnModelListener(moveColumnListener)
      sharedMouseListeners.forEach(view::removeMouseListener)
      // JTable and TableExpandableItemsHandler both listen to the row selection model. Replacing the shared model
      // lets each unregister from the primary view before the strip becomes otherwise unreachable.
      view.selectionModel = DefaultListSelectionModel()
      view.detachModel()
      Disposer.dispose(view)
    }
  }

  private fun mirrorColumnSelection(from: ListSelectionModel, to: ListSelectionModel, toMain: Boolean) {
    val frozen = frozenView ?: return
    if (mirroringSelection) return
    mirroringSelection = true
    // Mirroring is derived, so it must not scroll: the autoscroller would follow the lead row of the selection.
    try {
      runWithAutoscrollLocked(resultPanel) {
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

  fun afterChangeSelection(view: GridTable, columnIndex: Int, toggle: Boolean, extend: Boolean) {
    val frozen = frozenView
    if (view !== frozen || toggle || extend || columnIndex < 0 || columnIndex >= frozen.columnCount) return
    // The strip holds a subset of the columns, so its index means nothing in the main table: go through the model.
    val mainColumn = primaryView.viewColumnOf(frozen.columnModel.getColumn(columnIndex).modelIndex)
    if (mainColumn < 0) return
    primaryView.columnModel.selectionModel.setSelectionInterval(mainColumn, mainColumn)
  }

  fun processMouseEvent(view: GridTable, event: MouseEvent, defaultProcessor: Runnable) {
    if (!stopEditingBeforeNonLeftPress(view, event)) return
    // Strip presses and releases must not scroll the main viewport through selection notifications.
    if (view !== primaryView) {
      runWithAutoscrollLocked(resultPanel) {
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

  /**
   * Commits before a non-left press can change the selection. Swing handles left presses itself.
   *
   * @return false when the press has to be dropped, because an editor refused to commit.
   */
  private fun stopEditingBeforeNonLeftPress(view: GridTable, event: MouseEvent): Boolean =
    event.id != MouseEvent.MOUSE_PRESSED || SwingUtilities.isLeftMouseButton(event) || !isPairedView(view) ||
    stopEditingUnlessAt(modelCell(view, view.rowAtPoint(event.point), view.columnAtPoint(event.point)))

  fun processMouseMotionEvent(view: GridTable, event: MouseEvent) {
    if (event.id == MouseEvent.MOUSE_DRAGGED && !event.isConsumed && SwingUtilities.isLeftMouseButton(event)) {
      extendDragAcrossFrozenRegion(view, event)
    }
  }

  private fun extendDragAcrossFrozenRegion(view: GridTable, event: MouseEvent) {
    val frozen = frozenView ?: return
    if (dragAnchorColumn < 0 || dragAnchorRow < 0) return
    if (GridUtil.isIntervalModifierSet(event) || GridUtil.isExclusiveModifierSet(event)) return
    if (!isDragOverOtherRegion(view, event)) return
    val targetColumn = unifiedMainColumn(view, event)
    val targetRow = clampedRowAt(view, event)
    if (targetColumn < 0 || targetRow < 0) return
    // BasicTableUI cannot extend a drag into another JTable, so continue it through the unified primary selection.
    val selection = primaryView.gridSelectionModel() as? TableSelectionModel ?: return
    if (view !== primaryView) primaryView.columnModel.selectionModel.valueIsAdjusting = true
    selection.setRowSelectionInterval(dragAnchorRow, targetRow)
    // Pinned placeholders retain their main-table indices, so select the range in displayed order.
    selectDisplayedColumnRange(displayedColumnOrder(frozen), targetColumn, add = false, anchorOverride = dragAnchorColumn)
  }

  private fun isDragOverOtherRegion(view: GridTable, event: MouseEvent): Boolean =
    frozenView != null && (view !== primaryView) != isOverFrozenStrip(view, event)

  /** The strip is only as wide as its viewport shows, so the columns it hides do not belong to the region either. */
  private fun isOverFrozenStrip(view: GridTable, event: MouseEvent): Boolean {
    val frozen = frozenView ?: return false
    val viewport = layout.frozenViewport
                   ?: return SwingUtilities.convertPoint(view, event.point, frozen).x < frozen.width
    return SwingUtilities.convertPoint(view, event.point, viewport).x < viewport.width
  }

  private fun unifiedMainColumn(view: GridTable, event: MouseEvent): Int {
    val frozen = frozenView ?: return -1
    if (isOverFrozenStrip(view, event)) {
      if (frozen.width <= 0 || frozen.columnCount == 0) return -1
      val inFrozen = SwingUtilities.convertPoint(view, event.point, frozen)
      var frozenColumn = frozen.columnAtPoint(Point(inFrozen.x.coerceIn(0, frozen.width - 1), 0))
      if (frozenColumn < 0) frozenColumn = frozen.columnCount - 1
      val modelIndex = frozen.columnModel.getColumn(frozenColumn).modelIndex
      return primaryView.rawIndexConverter.column2View().applyAsInt(modelIndex)
    }
    if (primaryView.width <= 0) return -1
    val inMain = SwingUtilities.convertPoint(view, event.point, primaryView)
    return primaryView.columnAtPoint(Point(inMain.x.coerceIn(0, primaryView.width - 1), 0))
  }

  private fun clampedRowAt(view: GridTable, event: MouseEvent): Int {
    if (view.height <= 0) return -1
    return view.rowAtPoint(Point(0, event.y.coerceIn(0, view.height - 1)))
  }

  /** Handles horizontal moves while a strip exists, skipping hidden placeholders and retaining one unified anchor. */
  fun handleColumnMove(view: GridTable, forward: Boolean, extend: Boolean, wrapsRows: Boolean): Boolean {
    val frozen = frozenView ?: return false
    if (view !== frozen && view !== primaryView) return false
    val lead = leadColumn(view)
    if (lead !in 0 until view.columnCount) return false
    val order = displayedColumnOrder(frozen)
    val current = if (view === primaryView) lead else order.primaryIndexOfFrozen(lead) ?: return false
    val row = leadRow(view)
    if (row < 0) return false
    // Swing steps through column-model indices, including hidden pinned placeholders.
    val swingTarget = lead + if (forward) 1 else -1
    val swingHitsPlaceholder = view === primaryView && isPinnedPlaceholder(primaryView.columnModel, swingTarget)
    val target = order.adjacent(current, forward)
    // At the edge, Tab wraps rows; arrows must not enter a hidden placeholder.
    if (target == null) {
      return wrapsRows && !extend && continueInAdjacentRow(frozen, order, row, forward) || swingHitsPlaceholder
    }
    val focusTarget = if (isPinnedPlaceholder(primaryView.columnModel, target)) frozen else primaryView
    val handledHere = extend || focusTarget !== view || (view === primaryView && target != swingTarget)
    return handledHere && changeColumnSelection(order, row, target, extend, focusTarget)
  }

  /**
   * Wraps Tab/Shift+Tab through both tables instead of wrapping within the focused table alone.
   */
  private fun continueInAdjacentRow(frozen: GridTable, order: DisplayedColumnOrder, row: Int, forward: Boolean): Boolean {
    val rows = primaryView.rowCount
    if (rows == 0) return false
    val target = order.edge(forward) ?: return false
    val focusTarget = if (isPinnedPlaceholder(primaryView.columnModel, target)) frozen else primaryView
    val nextRow = if (forward) (row + 1) % rows else (row + rows - 1) % rows
    return changeColumnSelection(order, nextRow, target, false, focusTarget)
  }

  /** Handles moves to the first or last displayed column, including their selection-extending variants. */
  fun handleColumnEdgeMove(view: GridTable, first: Boolean, extend: Boolean): Boolean {
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
  fun handleCellRangeSelection(view: GridTable, row: Int, column: Int, add: Boolean): Boolean {
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
  fun selectDisplayedColumnRange(view: GridTable, column: Int, add: Boolean): Boolean {
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
                                    focusTarget: GridTable): Boolean {
    // A rejected edit must prevent scrolling and focus transfer as well as the selection change.
    if (!stopEditingBeforeSelectionChange(primaryView, row, target)) return true
    if (extend) {
      if (!selectDisplayedColumnRange(order, target, false)) return false
      if (focusTarget === primaryView) primaryView.scrollRectToVisible(primaryView.getCellRect(row, target, true))
    }
    else if (focusTarget === primaryView) primaryView.changeSelection(row, target, false, false)
    else runWithAutoscrollLocked(resultPanel) {
      // The selected placeholder is already represented in the fixed strip, so it must not move the main viewport.
      primaryView.changeSelection(row, target, false, false)
    }
    if (focusTarget !== primaryView) layout.scrollFrozenColumnToVisible(target)
    focusTarget.requestFocusInWindow()
    return true
  }

  /**
   * Commits before selection moves, since edits are applied to the selection present at commit time.
   * Exempts the edited cell because Swing opens an editor before selecting it. Without a strip, Swing handles this.
   *
   * @return false when an editor refused to commit, in which case the selection has to stay where it is.
   */
  fun stopEditingBeforeSelectionChange(view: GridTable, row: Int, column: Int): Boolean =
    frozenView == null || !isPairedView(view) || stopEditingUnlessAt(modelCell(view, row, column))

  private fun isPairedView(view: GridTable): Boolean = view === primaryView || view === frozenView

  /** Commits editors in either table unless they already edit the target cell. */
  private fun stopEditingUnlessAt(target: ModelCell?): Boolean {
    val frozen = frozenView
    return stopEditingUnlessAt(primaryView, target) && (frozen == null || stopEditingUnlessAt(frozen, target))
  }

  private fun stopEditingUnlessAt(view: GridTable, target: ModelCell?): Boolean {
    val editor = view.cellEditor ?: return true
    val editing = modelCell(view, view.editingRow, view.editingColumn)
    // Match Swing's commit path without the prompt added by GridTable.stopEditing().
    return (editing != null && editing == target) || editor.stopCellEditing()
  }

  /** The two tables index rows and columns on their own, so only model indices identify a cell in both. */
  private fun modelCell(view: GridTable, row: Int, column: Int): ModelCell? =
    if (row in 0 until view.rowCount && column in 0 until view.columnCount)
      ModelCell(view.convertRowIndexToModel(row), view.convertColumnIndexToModel(column))
    else null

  private data class ModelCell(val row: Int, val column: Int)

  /** Selects a range in displayed order: pinned columns followed by the visible main-table columns. */
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
      // Preserve drag batching until release to prevent selection-driven viewport jumps.
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

  private fun mainColumnOfView(order: DisplayedColumnOrder, view: GridTable, viewColumn: Int): Int {
    if (viewColumn !in 0 until view.columnCount) return -1
    val frozen = frozenView ?: return -1
    return if (view === primaryView) viewColumn
    else if (view === frozen) order.primaryIndexOfFrozen(viewColumn) ?: -1
    else -1
  }

  private fun displayedColumnOrder(frozen: GridTable): DisplayedColumnOrder = DisplayedColumnOrder(
    primaryModelOrder = (0 until primaryView.columnCount).map { primaryView.columnModel.getColumn(it).modelIndex },
    frozenModelOrder = (0 until frozen.columnCount).map { frozen.columnModel.getColumn(it).modelIndex },
  )

  private fun leadColumn(view: GridTable): Int = view.columnModel.selectionModel.leadSelectionIndex

  private fun leadRow(view: GridTable): Int = view.selectionModel.leadSelectionIndex

  fun addMouseListenerToComponents(listener: MouseListener) {
    componentMouseListeners.add(listener)
    frozenView?.addMouseListener(listener)
  }

  private fun findScrollPane(): TableScrollPane? = layout.findScrollPane()

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

internal fun isPinnedPlaceholder(columns: TableColumnModel, index: Int): Boolean =
  index in 0 until columns.columnCount && (columns.getColumn(index) as? TableResultViewColumn)?.isFrozenHidden == true

/** [JBAutoScroller.AutoscrollLocker] is not reentrant: an inner lock would otherwise release its outer caller. */
internal fun runWithAutoscrollLocked(grid: DataGrid, action: () -> Unit) {
  val locker = grid.autoscrollLocker
  if (locker.locked()) action()
  else locker.runWithLock { action() }
}
