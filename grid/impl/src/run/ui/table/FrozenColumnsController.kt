package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ViewIndex
import com.intellij.database.run.ui.grid.GridRowHeader
import com.intellij.openapi.Disposable
import com.intellij.ui.ComponentUtil
import com.intellij.ui.JBAutoScroller
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.ActionMap
import javax.swing.DefaultListSelectionModel
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.table.TableColumnModel

/**
 * Coordinates what crosses the boundary between the main table and the pinned table: navigation, selections, drags and edits that span
 * both, and the row numbers. The view creates and drops the pinned table and tells this controller; the controller never calls the view.
 * [GridTable] keeps rendering and ordinary table behavior. [FrozenStripLayout] places and scrolls the pinned table, and
 * [FrozenColumnResize] is the resize handle on the main header.
 */
internal class FrozenColumnsController(
  /** The view's tables and the columns each shows, which only the view changes. */
  private val tables: ViewTableLookup,
  /** The view's column model: the grid's columns in grid order, and their selection. */
  private val gridColumns: TableColumnModel,
  /** The view's row selection, which both tables share. */
  private val rowSelection: ListSelectionModel,
  private val grid: DataGrid,
) : Disposable {
  private val mainTable: GridTable = tables.main

  /** A position in the view's column model as a grid column, which it is while the grid isn't transposed, the only time it pins. */
  private fun gridColumnAt(position: Int): ViewIndex<GridColumn> = ViewIndex.forColumn(grid, position)

  /** The grid column of the lead, or null when the view's column model has no lead column. */
  private fun leadGridColumn(): ViewIndex<GridColumn>? =
    gridColumns.selectionModel.leadSelectionIndex.takeIf { it in 0 until gridColumns.columnCount }?.let(::gridColumnAt)

  private var region: FrozenRegion? = null
  private var dragAnchorRow = -1
  /** The grid column a left press started a drag on, or null without one. */
  private var dragAnchorColumn: ViewIndex<GridColumn>? = null
  /** The view's row header, which the view creates and disposes; null while it shows none. */
  private var rowHeader: GridRowHeader? = null

  /** The pinned table, or null while nothing is pinned. */
  private val pinnedTable: GridTable? get() = tables.pinned

  private var disposed = false

  /** Installs navigation that treats the pinned table and the main table as one displayed column sequence. */
  fun installColumnNavigationActions(table: GridTable, actionMap: ActionMap) {
    wrapColumnMoveAction(table, actionMap, "selectNextColumnCell", forward = true, extend = false, wrapsRows = true)
    wrapColumnMoveAction(table, actionMap, "selectPreviousColumnCell", forward = false, extend = false, wrapsRows = true)
    wrapColumnMoveAction(table, actionMap, "selectNextColumn", forward = true, extend = false)
    wrapColumnMoveAction(table, actionMap, "selectPreviousColumn", forward = false, extend = false)
    wrapColumnMoveAction(table, actionMap, "selectNextColumnExtendSelection", forward = true, extend = true)
    wrapColumnMoveAction(table, actionMap, "selectPreviousColumnExtendSelection", forward = false, extend = true)
    wrapColumnEdgeAction(table, actionMap, "selectFirstColumn", first = true, extend = false)
    wrapColumnEdgeAction(table, actionMap, "selectLastColumn", first = false, extend = false)
    wrapColumnEdgeAction(table, actionMap, "selectFirstColumnExtendSelection", first = true, extend = true)
    wrapColumnEdgeAction(table, actionMap, "selectLastColumnExtendSelection", first = false, extend = true)
    wrapNavigationAction(actionMap, "selectNextColumnChangeLead") { handleColumnLeadMove(table, forward = true) }
    wrapNavigationAction(actionMap, "selectPreviousColumnChangeLead") { handleColumnLeadMove(table, forward = false) }
    for (name in ROW_ACTIONS) wrapNavigationAction(actionMap, name) { runInLeadColumnTable(table, name) }
    wrapSelectAll(actionMap)
  }

  /** Swing's Select All selects the columns of its own table; this one selects every column and keeps the lead and anchor. */
  private fun wrapSelectAll(actionMap: ActionMap) {
    val original = actionMap["selectAll"] ?: return
    actionMap.put("selectAll", object : AbstractAction() {
      override fun actionPerformed(event: ActionEvent) {
        val columns = gridColumns.selectionModel
        val lead = columns.leadSelectionIndex
        val anchor = columns.anchorSelectionIndex
        original.actionPerformed(event)
        if (mainTable.rowCount == 0 || gridColumns.columnCount == 0) return
        columns.addSelectionInterval(0, gridColumns.columnCount - 1)
        TableSelectionModel.restoreLeadAndAnchor(columns, lead, anchor)
      }
    })
  }

  /**
   * Runs a row action in the table that shows the grid's lead column, when [table] doesn't show it, and moves the focus there.
   * Swing's row actions keep the lead column of their own table, which would be its first column for a lead it doesn't show.
   */
  private fun runInLeadColumnTable(table: GridTable, name: String): Boolean {
    if (pinnedTable == null || (table !== pinnedTable && table !== mainTable)) return false
    val lead = leadGridColumn() ?: return false
    if (tables.localColumn(table, lead) >= 0) return false
    val target = tables.tableOf(lead)
    val action = target.actionMap[name]
    if (target === table || action == null) return false
    action.actionPerformed(ActionEvent(target, ActionEvent.ACTION_PERFORMED, name))
    target.requestFocusInWindow()
    return true
  }

  private fun wrapColumnMoveAction(table: GridTable,
                                   actionMap: ActionMap,
                                   name: String,
                                   forward: Boolean,
                                   extend: Boolean,
                                   wrapsRows: Boolean = false) {
    wrapNavigationAction(actionMap, name) { handleColumnMove(table, forward, extend, wrapsRows) }
  }

  private fun wrapColumnEdgeAction(table: GridTable, actionMap: ActionMap, name: String, first: Boolean, extend: Boolean) {
    wrapNavigationAction(actionMap, name) { handleColumnEdgeMove(table, first, extend) }
  }

  private fun wrapNavigationAction(actionMap: ActionMap, name: String, handler: () -> Boolean) {
    val original = actionMap[name] ?: return
    actionMap.put(name, object : AbstractAction() {
      override fun actionPerformed(event: ActionEvent) {
        if (!handler()) original.actionPerformed(event)
      }
    })
  }

  /** A direct resize may shrink freely, but it may not introduce or increase avoidable pinned-strip overflow. */
  fun constrainFrozenColumnResize(table: GridTable, column: TableResultViewColumn, requestedWidth: Int): Int =
    region?.resize?.constrainFrozenColumnResize(table, column, requestedWidth) ?: requestedWidth

  /** Keeps the keyboard width action consistent with mouse resizing, including mixed and multi-column selections. */
  fun constrainSelectedColumnWidthDelta(selectedPinnedCount: Int, selectedUnpinnedCount: Int, delta: Int): Int =
    region?.resize?.constrainSelectedColumnWidthDelta(selectedPinnedCount, selectedUnpinnedCount, delta) ?: delta

  /** The width the columns of both tables share: the scroll pane's width without the row numbers and the vertical scroll bar. */
  fun getAvailableColumnsWidth(): Int {
    val parent = findScrollPane() ?: return maxOf(0, mainTable.width)
    region?.let { return it.layout.availableColumnsWidth() }
    val width = containerColumnsWidth(parent, if (rowHeader != null && !mainTable.isTransposed) shownRowHeaderWidth(parent) else 0)
    return if (width > 0) width else parent.viewport.extentSize.width
  }

  fun columnMarginChanged(table: GridTable) {
    region?.layout?.columnMarginChanged(table)
  }

  /** Shows [header], the view's row header, or none; while the pinned table is laid out, its row numbers go beside it. */
  fun showRowHeader(header: GridRowHeader?) {
    rowHeader = header
    val region = region
    if (region != null && !mainTable.isTransposed) {
      // The layout keeps its scroll pane, and the view disposes the header this replaces right after.
      region.layout.installFrozenRegion(header)
      region.layout.scrollPane.revalidate()
      region.layout.scrollPane.repaint()
      return
    }
    replaceRowHeaderView(findScrollPane() ?: return, header)
  }

  /** Lays out the pinned table, which the view added, and lays it out again after each change of the pins. */
  fun showPinnedTable() {
    if (disposed) return
    val pinned = pinnedTable ?: return
    val parent = findScrollPane() ?: return
    val region = region ?: createRegion(parent, pinned)
    val orientation = parent.componentOrientation
    mainTable.applyComponentOrientation(orientation)
    pinned.applyComponentOrientation(orientation)
    region.layout.installFrozenRegion(rowHeader)
    parent.revalidate()
    parent.repaint()
  }

  private fun createRegion(parent: TableScrollPane, pinned: GridTable): FrozenRegion {
    val layout = FrozenStripLayout(mainTable, pinned, parent)
    return FrozenRegion(layout, FrozenColumnResize(mainTable, pinned, layout)).also { region = it }
  }

  /** The view is about to drop the pinned table, so the scroll pane goes back to the main table alone, with its row numbers. */
  fun pinnedTableDropping() {
    if (disposed) return
    val parent = findScrollPane()
    releaseScrollPane(restoreCorners = parent != null)
    if (parent == null) return
    replaceRowHeaderView(parent, rowHeader)
    parent.revalidate()
    parent.repaint()
  }

  private fun releaseScrollPane(restoreCorners: Boolean) {
    val region = region ?: return
    this.region = null
    region.layout.uninstallFrozenScrolling()
    region.resize.uninstallColumnResizeHandle()
    if (restoreCorners) region.layout.restoreOriginalCorners()
  }

  override fun dispose() {
    disposed = true
    releaseScrollPane(restoreCorners = findScrollPane() != null)
  }

  /** The layout and the resize handle of the pinned table, created together for one pinned table and dropped together. */
  private class FrozenRegion(val layout: FrozenStripLayout, val resize: FrozenColumnResize)

  /** Brings the lead row into view in the main table while the lead is on a pinned column, which the main table's autoscroller skips. */
  fun revealPinnedLeadRow() {
    val pinned = pinnedTable ?: return
    if (grid.autoscrollLocker.locked()) return
    val rows = rowSelection
    val columns = gridColumns.selectionModel
    if (rows.isSelectionEmpty || columns.isSelectionEmpty) return
    val row = rows.leadSelectionIndex
    val lead = leadGridColumn() ?: return
    if (row in 0 until mainTable.rowCount && tables.tableOf(lead) === pinned) revealRow(row)
  }

  /** Scrolls the main table to [row] and leaves its columns where they are, for a cell the pinned table shows. */
  private fun revealRow(row: Int) {
    val cell = mainTable.getCellRect(row, -1, false)
    mainTable.scrollRectToVisible(Rectangle(mainTable.visibleRect.x, cell.y, 0, cell.height))
  }

  /** Whether [table] goes on with [event]: a non-left press first commits an open editor, and is dropped if the editor refuses. */
  fun beforeMouseEvent(table: GridTable, event: MouseEvent): Boolean = stopEditingBeforeNonLeftPress(table, event)

  /** [table] handled [event]; a left press starts a drag that may go on across the divider, from the cell it was on. */
  fun afterMouseEvent(table: GridTable, event: MouseEvent) {
    if (event.id == MouseEvent.MOUSE_PRESSED && SwingUtilities.isLeftMouseButton(event) && pinnedTable != null) {
      dragAnchorRow = clampedRowAt(table, event)
      dragAnchorColumn = unifiedGridColumn(table, event)
    }
  }

  /**
   * Commits before a non-left press can change the selection. Swing handles left presses itself.
   *
   * @return false when the press has to be dropped, because an editor refused to commit.
   */
  private fun stopEditingBeforeNonLeftPress(table: GridTable, event: MouseEvent): Boolean =
    event.id != MouseEvent.MOUSE_PRESSED || SwingUtilities.isLeftMouseButton(event) || !isViewTable(table) ||
    stopEditingUnlessAt(modelCell(table, table.rowAtPoint(event.point), table.columnAtPoint(event.point)))

  fun processMouseMotionEvent(table: GridTable, event: MouseEvent) {
    if (event.id == MouseEvent.MOUSE_DRAGGED && !event.isConsumed && SwingUtilities.isLeftMouseButton(event)) {
      extendDragAcrossFrozenRegion(table, event)
    }
  }

  private fun extendDragAcrossFrozenRegion(table: GridTable, event: MouseEvent) {
    val pinned = pinnedTable ?: return
    val anchorColumn = dragAnchorColumn ?: return
    if (dragAnchorRow < 0) return
    if (GridUtil.isIntervalModifierSet(event) || GridUtil.isExclusiveModifierSet(event)) return
    if (!isDragOverOtherRegion(table, event)) return
    val targetColumn = unifiedGridColumn(table, event) ?: return
    val targetRow = clampedRowAt(table, event)
    if (targetRow < 0) return
    // BasicTableUI cannot extend a drag into another JTable, so continue it through the grid's selection.
    val selection = mainTable.gridSelectionModel() as? TableSelectionModel ?: return
    if (table !== mainTable) gridColumns.selectionModel.valueIsAdjusting = true
    selection.setRowSelectionInterval(dragAnchorRow, targetRow)
    // Pinned columns keep their grid indices, so select the range in displayed order.
    selectDisplayedColumnRange(displayedColumnOrder(pinned), targetColumn, addToSelection = false, anchorOverride = anchorColumn)
  }

  private fun isDragOverOtherRegion(table: GridTable, event: MouseEvent): Boolean =
    pinnedTable != null && (table !== mainTable) != isOverFrozenStrip(table, event)

  /** The strip is only as wide as its viewport shows, so the columns it hides do not belong to the region either. */
  private fun isOverFrozenStrip(table: GridTable, event: MouseEvent): Boolean {
    val pinned = pinnedTable ?: return false
    val viewport = region?.layout?.frozenViewport
                   ?: return SwingUtilities.convertPoint(table, event.point, pinned).x < pinned.width
    return SwingUtilities.convertPoint(table, event.point, viewport).x < viewport.width
  }

  /** The grid column under [event], in whichever table it is over, or null. */
  private fun unifiedGridColumn(table: GridTable, event: MouseEvent): ViewIndex<GridColumn>? {
    val pinned = pinnedTable ?: return null
    if (isOverFrozenStrip(table, event)) {
      if (pinned.width <= 0 || pinned.columnCount == 0) return null
      val inPinned = SwingUtilities.convertPoint(table, event.point, pinned)
      var pinnedColumn = pinned.columnAtPoint(Point(inPinned.x.coerceIn(0, pinned.width - 1), 0))
      if (pinnedColumn < 0) pinnedColumn = pinned.columnCount - 1
      return gridColumnOfTable(pinned, pinnedColumn)
    }
    if (mainTable.width <= 0) return null
    val inMain = SwingUtilities.convertPoint(table, event.point, mainTable)
    return gridColumnOfTable(mainTable, mainTable.columnAtPoint(Point(inMain.x.coerceIn(0, mainTable.width - 1), 0)))
  }

  private fun clampedRowAt(table: GridTable, event: MouseEvent): Int {
    if (table.height <= 0) return -1
    return table.rowAtPoint(Point(0, event.y.coerceIn(0, table.height - 1)))
  }

  /** Handles horizontal moves while there is a pinned table, in displayed order and from the grid's lead, wherever the focus is. */
  fun handleColumnMove(table: GridTable, forward: Boolean, extend: Boolean, wrapsRows: Boolean): Boolean {
    val pinned = pinnedTable ?: return false
    if (table !== pinned && table !== mainTable) return false
    val current = leadGridColumn() ?: return false
    val order = displayedColumnOrder(pinned)
    val row = leadRow(table)
    if (row < 0) return false
    if (wrapsRows && !extend && moveWithinSelection(pinned, order, row, current, forward)) return true
    // The lead in the focused table, where Swing's own action would move it, or -1 when the other table shows the lead.
    val lead = tables.localColumn(table, current)
    val target = order.adjacent(current, forward)
    // At the edge, Tab wraps rows; Swing can't move a lead that the focused table doesn't show.
    if (target == null) {
      return wrapsRows && !extend && continueInAdjacentRow(order, row, forward) || lead < 0
    }
    val focusTarget = tables.tableOf(target)
    val swingTarget = lead + if (forward) 1 else -1
    val handledHere = extend || lead < 0 || focusTarget !== table || tables.localColumn(table, target) != swingTarget
    return handledHere && changeColumnSelection(order, row, target, extend, focusTarget)
  }

  /**
   * Tab inside a selection of several cells moves the lead through the selection and keeps it, as Swing does in one table. It steps through
   * the selected cells in displayed order, row by row, and wraps from the last one to the first.
   */
  private fun moveWithinSelection(pinned: GridTable,
                                  order: DisplayedColumnOrder<ViewIndex<GridColumn>>,
                                  row: Int,
                                  column: ViewIndex<GridColumn>,
                                  forward: Boolean): Boolean {
    val rows = rowSelection
    val columns = gridColumns.selectionModel
    val displayed = order.displayedColumns
    val selectedPositions = displayed.indices.filter { columns.isSelectedIndex(displayed[it].asInteger()) }
    if (rows.isSelectionEmpty || selectedPositions.isEmpty()) return false
    val oneCell = rows.minSelectionIndex == rows.maxSelectionIndex && selectedPositions.size == 1
    if (oneCell && rows.isSelectedIndex(row) && columns.isSelectedIndex(column.asInteger())) return false

    val firstColumn = selectedPositions.first()
    val lastColumn = selectedPositions.last()
    var nextRow = row.coerceIn(rows.minSelectionIndex - 1, rows.maxSelectionIndex + 1)
    var position = displayed.indexOf(column).coerceIn(firstColumn - 1, lastColumn + 1)
    do {
      position += if (forward) 1 else -1
      if (position > lastColumn) {
        position = firstColumn
        nextRow = if (nextRow >= rows.maxSelectionIndex) rows.minSelectionIndex else nextRow + 1
      }
      else if (position < firstColumn) {
        position = lastColumn
        nextRow = if (nextRow <= rows.minSelectionIndex) rows.maxSelectionIndex else nextRow - 1
      }
    }
    while (!rows.isSelectedIndex(nextRow) || !columns.isSelectedIndex(displayed[position].asInteger()))

    val target = displayed[position]
    if (!stopEditingBeforeGridSelectionChange(nextRow, target)) return true
    val inPinnedTable = tables.tableOf(target) === pinned
    // Selecting a selected index again moves the lead and the anchor to it and leaves the selection as it is.
    val moveLead = {
      rows.addSelectionInterval(nextRow, nextRow)
      columns.addSelectionInterval(target.asInteger(), target.asInteger())
    }
    if (inPinnedTable) {
      runWithAutoscrollLocked(grid, moveLead)
      region?.layout?.scrollFrozenColumnToVisible(tables.localColumn(pinned, target))
      revealRow(nextRow)
    }
    else {
      moveLead()
      mainTable.scrollRectToVisible(mainTable.getCellRect(nextRow, tables.localColumn(mainTable, target), false))
    }
    (if (inPinnedTable) pinned else mainTable).requestFocusInWindow()
    return true
  }

  /**
   * Wraps Tab/Shift+Tab through both tables instead of wrapping within the focused table alone.
   */
  private fun continueInAdjacentRow(order: DisplayedColumnOrder<ViewIndex<GridColumn>>, row: Int, forward: Boolean): Boolean {
    val rows = mainTable.rowCount
    if (rows == 0) return false
    val target = order.edge(forward) ?: return false
    val nextRow = if (forward) (row + 1) % rows else (row + rows - 1) % rows
    return changeColumnSelection(order, nextRow, target, false, tables.tableOf(target))
  }

  /** Handles moves to the first or last displayed column, including their selection-extending variants. */
  fun handleColumnEdgeMove(table: GridTable, first: Boolean, extend: Boolean): Boolean {
    val pinned = pinnedTable ?: return false
    if (table !== pinned && table !== mainTable) return false
    val order = displayedColumnOrder(pinned)
    val target = order.edge(first) ?: return false
    val row = leadRow(table)
    if (row < 0) return false
    return changeColumnSelection(order, row, target, extend, tables.tableOf(target))
  }

  /**
   * Moves the column lead to the adjacent displayed column and leaves the selection as it is, as Swing's "change lead" actions do in
   * one table. Swing still takes a step inside the focused table; this takes the step across the divider.
   */
  private fun handleColumnLeadMove(table: GridTable, forward: Boolean): Boolean {
    val pinned = pinnedTable ?: return false
    if (table !== pinned && table !== mainTable) return false
    val columns = gridColumns.selectionModel as? DefaultListSelectionModel ?: return false
    val current = leadGridColumn() ?: return false
    val lead = tables.localColumn(table, current)
    // At the edge Swing keeps the lead where it is, which it can only do for a lead the focused table shows.
    val target = displayedColumnOrder(pinned).adjacent(current, forward) ?: return lead < 0
    val focusTarget = tables.tableOf(target)
    val swingTarget = lead + if (forward) 1 else -1
    if (lead >= 0 && focusTarget === table && tables.localColumn(table, target) == swingTarget) return false
    if (!stopEditingUnlessAt(null)) return true
    columns.moveLeadSelectionIndex(target.asInteger())
    val rows = rowSelection as? DefaultListSelectionModel ?: return true
    if (rows.leadSelectionIndex !in 0 until mainTable.rowCount && mainTable.rowCount > 0) rows.moveLeadSelectionIndex(0)
    val row = rows.leadSelectionIndex
    val column = tables.localColumn(focusTarget, target)
    if (focusTarget === mainTable) {
      mainTable.scrollRectToVisible(mainTable.getCellRect(row, column, false))
    }
    else {
      region?.layout?.scrollFrozenColumnToVisible(column)
      revealRow(row)
    }
    focusTarget.requestFocusInWindow()
    return true
  }

  /** Applies a Shift+click cell range in displayed order, even when its anchor is in the other table; [localColumn] is [table]'s own. */
  fun handleCellRangeSelection(table: GridTable, row: Int, localColumn: Int, addToSelection: Boolean): Boolean {
    val pinned = pinnedTable ?: return false
    if ((table !== pinned && table !== mainTable) || row !in 0 until table.rowCount) return false
    val order = displayedColumnOrder(pinned)
    val target = gridColumnOfTable(table, localColumn) ?: return false

    val rows = rowSelection
    val rowAnchor = rows.anchorSelectionIndex.takeIf { it in 0 until mainTable.rowCount } ?: row
    if (addToSelection) rows.addSelectionInterval(rowAnchor, row)
    else rows.setSelectionInterval(rowAnchor, row)
    return selectDisplayedColumnRange(order, target, addToSelection)
  }

  /** Applies a whole-column Shift range from the unified anchor to [localColumn] of [table], in either table. */
  fun selectDisplayedColumnRange(table: GridTable, localColumn: Int, addToSelection: Boolean): Boolean {
    val pinned = pinnedTable ?: return false
    if (table !== pinned && table !== mainTable) return false
    val order = displayedColumnOrder(pinned)
    val target = gridColumnOfTable(table, localColumn) ?: return false
    return selectDisplayedColumnRange(order, target, addToSelection)
  }

  private fun changeColumnSelection(order: DisplayedColumnOrder<ViewIndex<GridColumn>>,
                                    row: Int,
                                    target: ViewIndex<GridColumn>,
                                    extend: Boolean,
                                    focusTarget: GridTable): Boolean {
    // A rejected edit must prevent scrolling and focus transfer as well as the selection change.
    if (!stopEditingBeforeGridSelectionChange(row, target)) return true
    val column = tables.localColumn(focusTarget, target)
    if (extend) {
      if (!selectDisplayedColumnRange(order, target, false)) return false
      if (focusTarget === mainTable) mainTable.scrollRectToVisible(mainTable.getCellRect(row, column, true))
    }
    else if (focusTarget === mainTable) mainTable.changeSelection(row, column, false, false)
    else runWithAutoscrollLocked(grid) {
      // The pinned cell is in the fixed strip already, so it must not move the main viewport.
      focusTarget.changeSelection(row, column, false, false)
    }
    if (focusTarget !== mainTable) {
      region?.layout?.scrollFrozenColumnToVisible(column)
      // Tab may have changed the row when it wrapped.
      revealRow(row)
    }
    focusTarget.requestFocusInWindow()
    return true
  }

  /**
   * Commits before selection moves, since edits are applied to the selection present at commit time.
   * Exempts the edited cell because Swing opens an editor before selecting it. Without a pinned table, Swing handles this.
   *
   * @return false when an editor refused to commit, in which case the selection has to stay where it is.
   */
  fun stopEditingBeforeSelectionChange(table: GridTable, row: Int, localColumn: Int): Boolean =
    pinnedTable == null || !isViewTable(table) || stopEditingUnlessAt(modelCell(table, row, localColumn))

  /** [stopEditingBeforeSelectionChange] for a cell given by its grid column. */
  private fun stopEditingBeforeGridSelectionChange(row: Int, column: ViewIndex<GridColumn>): Boolean =
    stopEditingUnlessAt(gridCell(row, column))

  private fun isViewTable(table: GridTable): Boolean = table === mainTable || table === pinnedTable

  /** Commits editors in either table unless they already edit the target cell. */
  private fun stopEditingUnlessAt(target: ModelCell?): Boolean {
    val pinned = pinnedTable
    return stopEditingUnlessAt(mainTable, target) && (pinned == null || stopEditingUnlessAt(pinned, target))
  }

  private fun stopEditingUnlessAt(table: GridTable, target: ModelCell?): Boolean {
    val editor = table.cellEditor ?: return true
    val editing = modelCell(table, table.editingRow, table.editingColumn)
    // Match Swing's commit path without the prompt added by GridTable.stopEditing().
    return (editing != null && editing == target) || editor.stopCellEditing()
  }

  /** The two tables index rows and columns on their own, so only model indices identify a cell in both. */
  private fun modelCell(table: GridTable, row: Int, localColumn: Int): ModelCell? =
    if (row in 0 until table.rowCount && localColumn in 0 until table.columnCount)
      modelCell(table.convertRowIndexToModel(row), table.convertColumnIndexToModel(localColumn))
    else null

  /** The cell at [row] of the tables, which share their rows, and at grid column [column], in model indices. */
  private fun gridCell(row: Int, column: ViewIndex<GridColumn>): ModelCell? =
    if (row in 0 until mainTable.rowCount && column.asInteger() in 0 until gridColumns.columnCount)
      modelCell(mainTable.convertRowIndexToModel(row), gridColumns.getColumn(column.asInteger()).modelIndex)
    else null

  private fun modelCell(modelRow: Int, modelColumn: Int) =
    ModelCell(ModelIndex.forRow(grid, modelRow), ModelIndex.forColumn(grid, modelColumn))

  private data class ModelCell(val row: ModelIndex<GridRow>, val column: ModelIndex<GridColumn>)

  /** Selects a range in displayed order, pinned columns first, in the view's column model, which both tables show. */
  private fun selectDisplayedColumnRange(order: DisplayedColumnOrder<ViewIndex<GridColumn>>,
                                         target: ViewIndex<GridColumn>,
                                         addToSelection: Boolean,
                                         anchorOverride: ViewIndex<GridColumn>? = null): Boolean {
    val selection = gridColumns.selectionModel
    val anchor = (anchorOverride ?: gridColumnAt(selection.anchorSelectionIndex)).takeIf(order::contains) ?: target
    val range = order.range(anchor, target) ?: return false

    // Preserve drag batching until release to prevent selection-driven viewport jumps.
    val wasAdjusting = selection.valueIsAdjusting
    selection.valueIsAdjusting = true
    try {
      if (!addToSelection) selection.clearSelection()
      // Every table's slice hears of each change, so the range goes in as few intervals as it has runs of adjacent columns.
      for (run in adjacentRuns(range)) {
        selection.addSelectionInterval(run.first, run.last)
      }
      selection.addSelectionInterval(target.asInteger(), target.asInteger())
      selection.anchorSelectionIndex = anchor.asInteger()
    }
    finally {
      selection.valueIsAdjusting = wasAdjusting
    }
    return true
  }

  /** [columns] as runs of adjacent positions in the view's column model, in the order the runs start. */
  private fun adjacentRuns(columns: List<ViewIndex<GridColumn>>): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    for (column in columns) {
      val position = column.asInteger()
      val last = runs.lastOrNull()
      if (last != null && position == last.last + 1) runs[runs.lastIndex] = last.first..position
      else runs.add(position..position)
    }
    return runs
  }

  /** The grid column at [localColumn] of [table], one of the two tables, or null. */
  private fun gridColumnOfTable(table: GridTable, localColumn: Int): ViewIndex<GridColumn>? =
    if (isViewTable(table)) tables.gridColumn(table, localColumn).takeIf { it.asInteger() >= 0 } else null

  /** The grid's columns in grid order, and the pinned table's in its own order. */
  private fun displayedColumnOrder(pinned: GridTable): DisplayedColumnOrder<ViewIndex<GridColumn>> = DisplayedColumnOrder(
    gridOrder = (0 until gridColumns.columnCount).map(::gridColumnAt),
    pinnedOrder = (0 until pinned.columnCount).map { tables.gridColumn(pinned, it) },
  )

  private fun leadRow(table: GridTable): Int = table.selectionModel.leadSelectionIndex

  private fun findScrollPane(): TableScrollPane? = ComponentUtil.getParentOfType(TableScrollPane::class.java, mainTable)
}

/** [JBAutoScroller.AutoscrollLocker] is not reentrant: an inner lock would otherwise release its outer caller. */
internal fun runWithAutoscrollLocked(grid: DataGrid, action: () -> Unit) {
  val locker = grid.autoscrollLocker
  if (locker.locked()) action()
  else locker.runWithLock { action() }
}

/** Shows [header] in the row header of [parent], or none. */
internal fun replaceRowHeaderView(parent: TableScrollPane, header: JComponent?) {
  if (header == null) parent.setRowHeader(null) else parent.setRowHeaderView(header)
}

/**
 * Swing's row actions of a table, which move or extend along the rows and keep the table's own lead column. Enter and Shift+Enter
 * stay out: the grid binds Enter to editing and disables Shift+Enter.
 */
private val ROW_ACTIONS = listOf(
  "selectNextRow", "selectPreviousRow", "selectNextRowExtendSelection", "selectPreviousRowExtendSelection",
  "selectNextRowChangeLead", "selectPreviousRowChangeLead",
  "scrollUpChangeSelection", "scrollDownChangeSelection", "scrollUpExtendSelection", "scrollDownExtendSelection",
  "selectFirstRow", "selectLastRow", "selectFirstRowExtendSelection", "selectLastRowExtendSelection",
)
