package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
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
import java.awt.event.MouseAdapter
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
  private val tables: ViewTables,
  /** The view's column model: the grid's columns in grid order, and their selection. */
  private val gridColumns: TableColumnModel,
  /** The view's columns in displayed order, which keys and drags follow across the divider. */
  private val displayedColumns: DisplayedColumns,
  /** The view's row selection, which both tables share. */
  private val rowSelection: ListSelectionModel,
  private val grid: DataGrid,
) : Disposable {
  private val mainTable: GridTable = tables.main

  /**
   * The grid column at [position] of the view's column model. The two match while the grid isn't transposed, which is the only time the
   * view pins columns.
   */
  private fun gridColumnAt(position: Int): ViewIndex<GridColumn> = ViewIndex.forColumn(grid, position)

  /** The grid column of the lead, or null when the view's column model has no lead column. */
  private fun leadGridColumn(): ViewIndex<GridColumn>? =
    gridColumns.selectionModel.leadSelectionIndex.takeIf { it in 0 until gridColumns.columnCount }?.let(::gridColumnAt)

  private var strip: PinnedStrip? = null
  /** The view row a left press started a drag on, or -1 without one; both tables share their rows. */
  private var dragAnchorRow = -1
  /** The grid column a left press started a drag on, or null without one. */
  private var dragAnchorColumn: ViewIndex<GridColumn>? = null
  /** The view's row header, which the view creates and disposes; null while it shows none. */
  private var rowHeader: GridRowHeader? = null

  /** The pinned table, or null while nothing is pinned. */
  private val pinnedTable: GridTable? get() = tables.pinned

  private var disposed = false

  /**
   * Wraps the navigation and Select All actions in [table]'s own action map, so they treat both tables as one displayed column sequence.
   * A theme change replaces only the parent map, so the wrappers stay; call this once per table.
   */
  fun installColumnNavigationActions(table: GridTable) {
    val actionMap = table.actionMap
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
        val columnSelection = gridColumns.selectionModel
        val lead = columnSelection.leadSelectionIndex
        val anchor = columnSelection.anchorSelectionIndex
        original.actionPerformed(event)
        if (mainTable.rowCount == 0 || gridColumns.columnCount == 0) return
        columnSelection.addSelectionInterval(0, gridColumns.columnCount - 1)
        TableSelectionModel.restoreLeadAndAnchor(columnSelection, lead, anchor)
      }
    })
  }

  /**
   * Runs a row action in the table that shows the grid's lead column, when [table] doesn't show it, and moves the focus there.
   * Swing's row actions keep the lead column of their own table, which would be its first column for a lead it doesn't show.
   * Keys typed before the view's delayed focus check runs, such as Down right after F3, reach the table without the lead.
   */
  private fun runInLeadColumnTable(table: GridTable, name: String): Boolean {
    if (pinnedTable == null || (table !== pinnedTable && table !== mainTable)) return false
    val lead = leadGridColumn() ?: return false
    if (tables.localIndexOf(table, lead) >= 0) return false
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

  /** [handler] returns true when it took the key, also when it refused the move; Swing's own action then doesn't run. */
  private fun wrapNavigationAction(actionMap: ActionMap, name: String, handler: () -> Boolean) {
    val original = actionMap[name] ?: return
    actionMap.put(name, object : AbstractAction() {
      override fun actionPerformed(event: ActionEvent) {
        if (!handler()) original.actionPerformed(event)
      }
    })
  }

  /**
   * The width [column] of [table] takes for a user resize to [requestedWidth]. A pinned column shrinks freely, but grows only while the
   * columns fit and never adds to an overflow.
   */
  fun constrainColumnResize(table: GridTable, column: TableResultViewColumn, requestedWidth: Int): Int =
    strip?.resize?.constrainColumnResize(table, column, requestedWidth) ?: requestedWidth

  /** The largest part of [delta] that each selected column can grow by while the columns still fit; see [FrozenColumnResize]. */
  fun constrainSelectedColumnWidthDelta(selectedPinnedCount: Int, selectedUnpinnedCount: Int, delta: Int): Int =
    strip?.resize?.constrainSelectedColumnWidthDelta(selectedPinnedCount, selectedUnpinnedCount, delta) ?: delta

  /** The width the columns of both tables share: the scroll pane's width without the row numbers and the vertical scroll bar. */
  fun getAvailableColumnsWidth(): Int {
    val scrollPane = findScrollPane() ?: return maxOf(0, mainTable.width)
    strip?.let { return it.layout.availableColumnsWidth() }
    val width =
      containerColumnsWidth(scrollPane, if (rowHeader != null && !mainTable.isTransposed) shownRowHeaderWidth(scrollPane) else 0)
    return if (width > 0) width else scrollPane.viewport.extentSize.width
  }

  fun columnMarginChanged(table: GridTable) {
    strip?.layout?.columnMarginChanged(table)
  }

  /** Shows [header], the view's row header, or none. While the pinned table is laid out, the row numbers go beside the pinned table. */
  fun showRowHeader(header: GridRowHeader?) {
    rowHeader = header
    val strip = strip
    if (strip != null && !mainTable.isTransposed) {
      // The layout keeps its scroll pane, and the view disposes the header this replaces right after.
      strip.layout.layOut(header)
      strip.layout.scrollPane.revalidate()
      strip.layout.scrollPane.repaint()
      return
    }
    replaceRowHeaderView(findScrollPane() ?: return, header)
  }

  /** Lays out the pinned table that the view added. The view calls this again after each change of the pins. */
  fun showPinnedTable() {
    if (disposed) return
    val pinned = pinnedTable ?: return
    val scrollPane = findScrollPane() ?: return
    val strip = strip ?: createStrip(scrollPane, pinned)
    val orientation = scrollPane.componentOrientation
    mainTable.applyComponentOrientation(orientation)
    pinned.applyComponentOrientation(orientation)
    strip.layout.layOut(rowHeader)
    scrollPane.revalidate()
    scrollPane.repaint()
  }

  private fun createStrip(scrollPane: TableScrollPane, pinned: GridTable): PinnedStrip {
    val layout = FrozenStripLayout(mainTable, pinned, scrollPane)
    return PinnedStrip(layout, FrozenColumnResize(mainTable, pinned, layout)).also { strip = it }
  }

  /** The view is about to drop the pinned table, so the scroll pane goes back to the main table alone, with its row numbers. */
  fun pinnedTableDropping() {
    if (disposed) return
    val scrollPane = findScrollPane()
    dropStrip(restoreCorners = scrollPane != null)
    if (scrollPane == null) return
    replaceRowHeaderView(scrollPane, rowHeader)
    scrollPane.revalidate()
    scrollPane.repaint()
  }

  private fun dropStrip(restoreCorners: Boolean) {
    val strip = strip ?: return
    this.strip = null
    strip.layout.uninstall()
    strip.resize.uninstallColumnResizeHandle()
    if (restoreCorners) strip.layout.restoreOriginalCorners()
  }

  override fun dispose() {
    disposed = true
    dropStrip(restoreCorners = findScrollPane() != null)
  }

  /** The layout and the resize handle of one pinned table, created and dropped together. */
  private class PinnedStrip(val layout: FrozenStripLayout, val resize: FrozenColumnResize)

  /** Brings the lead row into view in the main table while the lead is on a pinned column, which the main table's autoscroller skips. */
  fun revealPinnedLeadRow() {
    val pinned = pinnedTable ?: return
    if (grid.autoscrollLocker.locked()) return
    if (rowSelection.isSelectionEmpty || gridColumns.selectionModel.isSelectionEmpty) return
    val row = rowSelection.leadSelectionIndex
    val lead = leadGridColumn() ?: return
    if (row in 0 until mainTable.rowCount && tables.tableOf(lead) === pinned) revealRow(row)
  }

  /** Scrolls the main table to [row] and leaves its columns where they are, for a cell the pinned table shows. */
  private fun revealRow(row: Int) {
    val cell = mainTable.getCellRect(row, -1, false)
    mainTable.scrollRectToVisible(Rectangle(mainTable.visibleRect.x, cell.y, 0, cell.height))
  }

  /**
   * Whether [table] goes on with [event]. A non-left press first commits an open editor, and is dropped if the editor refuses. So does a
   * left press that is the popup trigger, a Ctrl+click on macOS: JBTable turns it into a right press only after this check. Swing handles
   * other left presses itself.
   */
  fun beforeMouseEvent(table: GridTable, event: MouseEvent): Boolean =
    event.id != MouseEvent.MOUSE_PRESSED || (SwingUtilities.isLeftMouseButton(event) && !event.isPopupTrigger) || !isViewTable(table) ||
    stopEditingUnlessAt(table.modelCellAt(table.rowAtPoint(event.point), table.columnAtPoint(event.point)))

  /** A left press starts a drag that may go on across the divider, from the cell it was on. */
  private val dragAnchorListener = object : MouseAdapter() {
    override fun mousePressed(event: MouseEvent) {
      val table = event.component as? GridTable ?: return
      if (SwingUtilities.isLeftMouseButton(event) && pinnedTable != null) {
        dragAnchorRow = clampedRowAt(table, event)
        dragAnchorColumn = gridColumnNear(table, event)
      }
    }
  }

  /** Lets a left press in [table] anchor a drag that may go on across the divider. */
  fun addDragAnchorListener(table: GridTable): Unit = table.addMouseListener(dragAnchorListener)

  fun removeDragAnchorListener(table: GridTable): Unit = table.removeMouseListener(dragAnchorListener)

  fun processMouseMotionEvent(table: GridTable, event: MouseEvent) {
    if (event.id == MouseEvent.MOUSE_DRAGGED && !event.isConsumed && SwingUtilities.isLeftMouseButton(event)) {
      extendDragAcrossDivider(table, event)
    }
  }

  private fun extendDragAcrossDivider(table: GridTable, event: MouseEvent) {
    if (pinnedTable == null) return
    val anchorColumn = dragAnchorColumn ?: return
    if (dragAnchorRow < 0) return
    if (GridUtil.isIntervalModifierSet(event) || GridUtil.isExclusiveModifierSet(event)) return
    if (!isOverOtherTable(table, event)) return
    val targetColumn = gridColumnNear(table, event) ?: return
    val targetRow = clampedRowAt(table, event)
    if (targetRow < 0) return
    // Rows can go away during a drag; the drag then stops extending.
    if (dragAnchorRow >= mainTable.rowCount || targetRow >= mainTable.rowCount) return
    // BasicTableUI cannot extend a drag into another JTable, so continue it through the view's selection.
    if (table !== mainTable) gridColumns.selectionModel.valueIsAdjusting = true
    rowSelection.setSelectionInterval(dragAnchorRow, targetRow)
    // Pinned columns keep their grid indices, so select the range in displayed order.
    displayedColumns.select(anchorColumn.asInteger(), targetColumn.asInteger(), RangeChange.REPLACE)
  }

  private fun isOverOtherTable(table: GridTable, event: MouseEvent): Boolean =
    pinnedTable != null && (table !== mainTable) != isOverPinnedViewport(table, event)

  /** Whether [event] is over the part of the pinned table that its viewport shows; columns scrolled out of the viewport don't count. */
  private fun isOverPinnedViewport(table: GridTable, event: MouseEvent): Boolean {
    val pinned = pinnedTable ?: return false
    val viewport = strip?.layout?.pinnedViewport
                   ?: return SwingUtilities.convertPoint(table, event.point, pinned).x < pinned.width
    return SwingUtilities.convertPoint(table, event.point, viewport).x < viewport.width
  }

  /** The grid column under [event], or the nearest one in the table it is over, or null. */
  private fun gridColumnNear(table: GridTable, event: MouseEvent): ViewIndex<GridColumn>? {
    val pinned = pinnedTable ?: return null
    if (isOverPinnedViewport(table, event)) {
      if (pinned.width <= 0 || pinned.columnCount == 0) return null
      val pointInPinned = SwingUtilities.convertPoint(table, event.point, pinned)
      var pinnedLocalIndex = pinned.columnAtPoint(Point(pointInPinned.x.coerceIn(0, pinned.width - 1), 0))
      if (pinnedLocalIndex < 0) pinnedLocalIndex = pinned.columnCount - 1
      return gridColumnOfTable(pinned, pinnedLocalIndex)
    }
    if (mainTable.width <= 0) return null
    val pointInMain = SwingUtilities.convertPoint(table, event.point, mainTable)
    return gridColumnOfTable(mainTable, mainTable.columnAtPoint(Point(pointInMain.x.coerceIn(0, mainTable.width - 1), 0)))
  }

  private fun clampedRowAt(table: GridTable, event: MouseEvent): Int {
    if (table.height <= 0) return -1
    return table.rowAtPoint(Point(0, event.y.coerceIn(0, table.height - 1)))
  }

  /** Handles horizontal moves while there is a pinned table, in displayed order and from the grid's lead, wherever the focus is. */
  fun handleColumnMove(table: GridTable, forward: Boolean, extend: Boolean, wrapsRows: Boolean): Boolean {
    val pinned = pinnedTable ?: return false
    if (table !== pinned && table !== mainTable) return false
    val leadColumn = leadGridColumn() ?: return false
    val order = displayedOrder()
    val row = leadRow(table)
    if (row < 0) return false
    if (wrapsRows && !extend && moveWithinSelection(pinned, order, row, leadColumn, forward)) return true
    // The lead in the focused table, where Swing's own action would move it, or -1 when the other table shows the lead.
    val localLead = tables.localIndexOf(table, leadColumn)
    val target = order.adjacent(leadColumn, forward)
    // At the edge, Tab wraps rows; Swing can't move a lead that the focused table doesn't show.
    if (target == null) {
      return wrapsRows && !extend && continueInAdjacentRow(order, row, forward) || localLead < 0
    }
    val focusTarget = tables.tableOf(target)
    val localSwingTarget = localLead + if (forward) 1 else -1
    val handledHere = extend || localLead < 0 || focusTarget !== table || tables.localIndexOf(table, target) != localSwingTarget
    return handledHere && changeColumnSelection(row, target, extend, focusTarget)
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
    val columnSelection = gridColumns.selectionModel
    val displayed = order.displayedColumns
    val selectedDisplayedPositions = displayed.indices.filter { columnSelection.isSelectedIndex(displayed[it].asInteger()) }
    if (rowSelection.isSelectionEmpty || selectedDisplayedPositions.isEmpty()) return false
    val oneCell = rowSelection.minSelectionIndex == rowSelection.maxSelectionIndex && selectedDisplayedPositions.size == 1
    if (oneCell && rowSelection.isSelectedIndex(row) && columnSelection.isSelectedIndex(column.asInteger())) return false

    val firstSelected = selectedDisplayedPositions.first()
    val lastSelected = selectedDisplayedPositions.last()
    var nextRow = row.coerceIn(rowSelection.minSelectionIndex - 1, rowSelection.maxSelectionIndex + 1)
    var displayedPosition = displayed.indexOf(column).coerceIn(firstSelected - 1, lastSelected + 1)
    do {
      displayedPosition += if (forward) 1 else -1
      if (displayedPosition > lastSelected) {
        displayedPosition = firstSelected
        nextRow = if (nextRow >= rowSelection.maxSelectionIndex) rowSelection.minSelectionIndex else nextRow + 1
      }
      else if (displayedPosition < firstSelected) {
        displayedPosition = lastSelected
        nextRow = if (nextRow <= rowSelection.minSelectionIndex) rowSelection.maxSelectionIndex else nextRow - 1
      }
    }
    while (!rowSelection.isSelectedIndex(nextRow) || !columnSelection.isSelectedIndex(displayed[displayedPosition].asInteger()))

    val target = displayed[displayedPosition]
    if (!stopEditingBeforeGridSelectionChange(nextRow, target)) return true
    val inPinnedTable = tables.tableOf(target) === pinned
    // Selecting a selected index again moves the lead and the anchor to it and leaves the selection as it is.
    val moveLead = {
      rowSelection.addSelectionInterval(nextRow, nextRow)
      columnSelection.addSelectionInterval(target.asInteger(), target.asInteger())
    }
    if (inPinnedTable) {
      runWithAutoscrollLocked(grid.autoscrollLocker, moveLead)
      strip?.layout?.scrollPinnedColumnToVisible(tables.localIndexOf(pinned, target))
      revealRow(nextRow)
    }
    else {
      moveLead()
      mainTable.scrollRectToVisible(mainTable.getCellRect(nextRow, tables.localIndexOf(mainTable, target), false))
    }
    (if (inPinnedTable) pinned else mainTable).requestFocusInWindow()
    return true
  }

  /**
   * Wraps Tab/Shift+Tab through both tables instead of wrapping within the focused table alone.
   */
  private fun continueInAdjacentRow(order: DisplayedColumnOrder<ViewIndex<GridColumn>>, row: Int, forward: Boolean): Boolean {
    val rowCount = mainTable.rowCount
    if (rowCount == 0) return false
    val target = (if (forward) order.first() else order.last()) ?: return false
    val nextRow = if (forward) (row + 1) % rowCount else (row + rowCount - 1) % rowCount
    return changeColumnSelection(nextRow, target, false, tables.tableOf(target))
  }

  /** Handles moves to the first or last displayed column, including their selection-extending variants. */
  fun handleColumnEdgeMove(table: GridTable, first: Boolean, extend: Boolean): Boolean {
    val pinned = pinnedTable ?: return false
    if (table !== pinned && table !== mainTable) return false
    val order = displayedOrder()
    val target = (if (first) order.first() else order.last()) ?: return false
    val row = leadRow(table)
    if (row < 0) return false
    return changeColumnSelection(row, target, extend, tables.tableOf(target))
  }

  /**
   * Moves the column lead to the adjacent displayed column and leaves the selection as it is, as Swing's "change lead" actions do in
   * one table. Swing still takes a step inside the focused table; this takes the step across the divider.
   */
  private fun handleColumnLeadMove(table: GridTable, forward: Boolean): Boolean {
    val pinned = pinnedTable ?: return false
    if (table !== pinned && table !== mainTable) return false
    val columnSelection = gridColumns.selectionModel as? DefaultListSelectionModel ?: return false
    val leadColumn = leadGridColumn() ?: return false
    val localLead = tables.localIndexOf(table, leadColumn)
    // At the edge Swing keeps the lead where it is, which it can only do for a lead the focused table shows.
    val target = displayedOrder().adjacent(leadColumn, forward) ?: return localLead < 0
    val focusTarget = tables.tableOf(target)
    val localSwingTarget = localLead + if (forward) 1 else -1
    if (localLead >= 0 && focusTarget === table && tables.localIndexOf(table, target) == localSwingTarget) return false
    if (!stopEditingUnlessAt(null)) return true
    columnSelection.moveLeadSelectionIndex(target.asInteger())
    val defaultRowSelection = rowSelection as? DefaultListSelectionModel ?: return true
    if (defaultRowSelection.leadSelectionIndex !in 0 until mainTable.rowCount && mainTable.rowCount > 0) {
      defaultRowSelection.moveLeadSelectionIndex(0)
    }
    val row = defaultRowSelection.leadSelectionIndex
    val localIndex = tables.localIndexOf(focusTarget, target)
    if (focusTarget === mainTable) {
      mainTable.scrollRectToVisible(mainTable.getCellRect(row, localIndex, false))
    }
    else {
      strip?.layout?.scrollPinnedColumnToVisible(localIndex)
      revealRow(row)
    }
    focusTarget.requestFocusInWindow()
    return true
  }

  private fun changeColumnSelection(row: Int, target: ViewIndex<GridColumn>, extend: Boolean, focusTarget: GridTable): Boolean {
    // A rejected edit must prevent scrolling and focus transfer as well as the selection change.
    if (!stopEditingBeforeGridSelectionChange(row, target)) return true
    val localIndex = tables.localIndexOf(focusTarget, target)
    if (extend) {
      val anchor = gridColumns.selectionModel.anchorSelectionIndex
      if (!displayedColumns.select(anchor, target.asInteger(), RangeChange.REPLACE)) return false
      if (focusTarget === mainTable) mainTable.scrollRectToVisible(mainTable.getCellRect(row, localIndex, true))
    }
    else if (focusTarget === mainTable) mainTable.changeSelection(row, localIndex, false, false)
    else runWithAutoscrollLocked(grid.autoscrollLocker) {
      // The pinned cell is in the pinned strip already, so it must not move the main viewport.
      focusTarget.changeSelection(row, localIndex, false, false)
    }
    if (focusTarget !== mainTable) {
      strip?.layout?.scrollPinnedColumnToVisible(localIndex)
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
  fun stopEditingBeforeSelectionChange(table: GridTable, target: ModelCell?): Boolean =
    pinnedTable == null || !isViewTable(table) || stopEditingUnlessAt(target)

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
    val editing = table.modelCellAt(table.editingRow, table.editingColumn)
    // Match Swing's commit path without the prompt added by TableResultView.stopEditing().
    return (editing != null && editing == target) || editor.stopCellEditing()
  }

  /** The cell at [row] of the tables, which share their rows, and at grid column [column], in model indices. */
  private fun gridCell(row: Int, column: ViewIndex<GridColumn>): ModelCell? {
    if (row !in 0 until mainTable.rowCount || column.asInteger() !in 0 until gridColumns.columnCount) return null
    val modelRow = ModelIndex.forRow(grid, mainTable.convertRowIndexToModel(row))
    val modelColumn = ModelIndex.forColumn(grid, gridColumns.getColumn(column.asInteger()).modelIndex)
    return if (modelRow.asInteger() >= 0 && modelColumn.asInteger() >= 0) ModelCell(modelRow, modelColumn) else null
  }

  /** The grid column at [localIndex] of [table], one of the two tables, or null. */
  private fun gridColumnOfTable(table: GridTable, localIndex: Int): ViewIndex<GridColumn>? =
    if (isViewTable(table)) tables.gridColumn(table, localIndex).takeIf { it.asInteger() >= 0 } else null

  /** The columns as the user sees them, as grid columns. */
  private fun displayedOrder(): DisplayedColumnOrder<ViewIndex<GridColumn>> = displayedColumns.order().map(::gridColumnAt)

  private fun leadRow(table: GridTable): Int = table.selectionModel.leadSelectionIndex

  private fun findScrollPane(): TableScrollPane? = ComponentUtil.getParentOfType(TableScrollPane::class.java, mainTable)
}

/**
 * Runs [action] with the grid's autoscroll [locker] locked, joining a lock a caller already holds. [JBAutoScroller.AutoscrollLocker] is
 * not reentrant, so an inner lock would release the outer one.
 */
internal fun runWithAutoscrollLocked(locker: JBAutoScroller.AutoscrollLocker, action: () -> Unit) {
  if (locker.locked()) action()
  else locker.runWithLock { action() }
}

/** Shows [header] in the row header of [scrollPane], or none. */
internal fun replaceRowHeaderView(scrollPane: TableScrollPane, header: JComponent?) {
  if (header == null) scrollPane.setRowHeader(null) else scrollPane.setRowHeaderView(header)
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
