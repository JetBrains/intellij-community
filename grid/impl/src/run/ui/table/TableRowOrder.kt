package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import java.awt.Rectangle
import javax.swing.DefaultListSelectionModel
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.RowSorterEvent
import javax.swing.event.RowSorterListener
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import javax.swing.table.TableColumnModel
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter
import kotlin.math.max

/**
 * The row order and filter of the view: one sorter for every table, which no table notifies itself. For each change of the data, the sort
 * or the filter, the view updates the sorter. The view keeps the shared row selection and each open editor on the same records, applies the
 * row heights again and repaints every table. JTable does this for a table with a sorter of its own; with one sorter for two tables, only
 * one party may do it.
 */
internal class TableRowOrder(
  private val grid: DataGrid,
  /** The view's row selection, which every table shares. */
  private val rows: ListSelectionModel,
  /** The view's column model, whose selection is the grid's column selection. */
  private val columnModel: TableColumnModel,
  private val rowHeights: RowHeightModel,
  /** The view's tables, which keep their edits through a change of the row order. */
  private val tables: ViewTableLookup,
) {
  /** The main table, which shows the view's data model; the view gives it a new one on a transpose, which fires `"model"`. */
  private val main: GridTable = tables.main

  private val dataListener = TableModelListener(::dataChanged)
  private val sorterListener = RowSorterListener(::sorterChanged)

  /** The selected records while a filter hides rows, so that a selected record the filter hides is selected again when shown. */
  private var keptSelection: DefaultListSelectionModel? = null
  private var restoringSelection = false
  private var notifyingSorter = false
  private var sortedWhileNotifying = false

  var sorter: TableRowSorter<TableModel> = createSorter()
    private set

  init {
    main.model.addTableModelListener(dataListener)
    // A transpose gives the main table a new data model, which needs its own sorter and whose rows have no heights yet.
    main.addPropertyChangeListener("model") { event ->
      (event.oldValue as TableModel).removeTableModelListener(dataListener)
      (event.newValue as TableModel).addTableModelListener(dataListener)
      sorter.removeRowSorterListener(sorterListener)
      keptSelection = null
      rowHeights.clear()
      sorter = createSorter()
    }
    rows.addListSelectionListener { if (!restoringSelection) keptSelection = null }
  }

  fun updateSortKeys() {
    sorter.setSortKeys(main.sortKeysFromColumnAttributes())
  }

  fun updateRowFilter() {
    sorter.setRowFilter(main.createFilter())
  }

  /** The tables are refreshed as for a new structure of the model, so the sorter forgets the order as a table's own sorter would. */
  fun structureChanged() {
    keptSelection = null
    rowHeights.clear()
    notifySorter { sorter.modelStructureChanged() }
  }

  private fun createSorter(): TableRowSorter<TableModel> {
    val created = main.createGridRowSorter()
    created.addRowSorterListener(sorterListener)
    for (table in tables.all()) table.setSharedRowSorter(created)
    // The filter reads the grid, which is set up from the new model only after this event.
    SwingUtilities.invokeLater { if (sorter === created) updateRowFilter() }
    return created
  }

  private fun notifySorter(notification: () -> Unit) {
    notifyingSorter = true
    sortedWhileNotifying = false
    try {
      notification()
    }
    finally {
      notifyingSorter = false
    }
  }

  private fun sorterChanged(event: RowSorterEvent) {
    when (event.type) {
      RowSorterEvent.Type.SORT_ORDER_CHANGED -> tables.all().forEach { it.tableHeader?.repaint() }
      RowSorterEvent.Type.SORTED -> if (notifyingSorter) sortedWhileNotifying = true else sorted(event)
      else -> Unit
    }
  }

  /** The sort keys or the filter changed: the view rows before it are read through the event. */
  private fun sorted(event: RowSorterEvent) {
    val toModel = { viewRow: Int ->
      when {
        viewRow < 0 -> -1
        event.previousRowCount == 0 -> viewRow
        else -> event.convertPreviousRowIndexToModel(viewRow)
      }
    }
    val filtering = sorter.viewRowCount != main.model.rowCount
    if (keptSelection == null && filtering) keptSelection = selectionInModelRows(toModel)
    val snapshot = if (keptSelection == null) SelectionSnapshot.take(rows, toModel) else null
    val editedRows = editedModelRows(toModel)
    rowsReordered(snapshot, editedRows) { it }
  }

  private fun dataChanged(event: TableModelEvent) {
    if (event.firstRow == TableModelEvent.HEADER_ROW) {
      // Each table clears its selection and repaints itself for a new structure.
      structureChanged()
      return
    }
    grid.autoscrollLocker.runWithLock { rowsChanged(RowsChange(event, main.model.rowCount)) }
    grid.fireContentChanged((event as? GridTableModel.RequestedTableModelEvent)?.place)
  }

  private fun rowsChanged(change: RowsChange) {
    if (change.type == TableModelEvent.UPDATE && !change.allRows && !sorter.sortsOnUpdates) {
      // The sorter doesn't sort again on an update, so the rows keep their order.
      notifySorter { notifyUpdate(change) }
      repaintUpdatedRows(change)
      return
    }
    val toModel = { viewRow: Int -> if (viewRow < 0 || viewRow >= sorter.viewRowCount) -1 else sorter.convertRowIndexToModel(viewRow) }
    var snapshot: SelectionSnapshot? = null
    when {
      change.allRows -> keptSelection = null
      keptSelection != null -> keptSelection?.let(change::moveRows)
      else -> snapshot = SelectionSnapshot.take(rows, toModel)
    }
    val editedRows = if (change.allRows) tables.all().filter { it.isEditing }.associateWith { -1 } else editedModelRows(toModel)
    when {
      change.allRows -> rowHeights.clear()
      change.type == TableModelEvent.INSERT -> rowHeights.rowsInserted(change.first, change.last)
      change.type == TableModelEvent.DELETE -> rowHeights.rowsDeleted(change.first, change.last)
    }

    notifySorter {
      when {
        change.allRows -> sorter.allRowsChanged()
        change.type == TableModelEvent.INSERT -> sorter.rowsInserted(change.first, change.last)
        change.type == TableModelEvent.DELETE -> sorter.rowsDeleted(change.first, change.last)
        else -> notifyUpdate(change)
      }
    }

    if (change.type == TableModelEvent.UPDATE && !change.allRows && !sortedWhileNotifying) {
      repaintUpdatedRows(change)
      return
    }
    rowsReordered(snapshot, editedRows, change::modelRowAfter)
    if (change.allRows) clearSelectionAndLeadAnchor()
  }

  private fun notifyUpdate(change: RowsChange) {
    if (change.column == TableModelEvent.ALL_COLUMNS) sorter.rowsUpdated(change.first, change.last)
    else sorter.rowsUpdated(change.first, change.last, change.column)
  }

  /** Puts the selection and the editors back on their records, and lays the tables out for the new row order. */
  private fun rowsReordered(snapshot: SelectionSnapshot?, editedRows: Map<GridTable, Int>, modelRowAfter: (Int) -> Int) {
    val toView = { modelRow: Int -> toView(modelRowAfter(modelRow)) }
    restoringSelection = true
    try {
      val kept = keptSelection
      if (snapshot != null) snapshot.restore(rows, toView)
      else if (kept != null) restoreKeptSelection(kept)
    }
    finally {
      restoringSelection = false
    }
    for ((table, modelRow) in editedRows) {
      val row = if (modelRow < 0) -1 else toView(modelRow)
      if (row < 0) cancelEditing(table)
      else {
        table.editingRow = row
        table.repaint()
      }
    }
    for (table in tables.all()) {
      // Without a sorter of its own a table keeps its row heights by view row, so it applies them again.
      table.setRowHeight(table.rowHeight)
      table.revalidate()
      table.repaint()
    }
  }

  private fun toView(modelRow: Int): Int =
    if (modelRow < 0 || modelRow >= main.model.rowCount) -1 else sorter.convertRowIndexToView(modelRow)

  private fun selectionInModelRows(toModel: (Int) -> Int): DefaultListSelectionModel {
    val view = rows
    val kept = DefaultListSelectionModel()
    for (row in selectedRows(view)) {
      val modelRow = toModel(row)
      if (modelRow >= 0) kept.addSelectionInterval(modelRow, modelRow)
    }
    setLeadAndAnchor(kept, toModel(view.leadSelectionIndex))
    return kept
  }

  private fun restoreKeptSelection(kept: DefaultListSelectionModel) {
    val view = rows
    view.valueIsAdjusting = true
    view.clearSelection()
    for (modelRow in selectedRows(kept)) {
      val row = toView(modelRow)
      if (row >= 0) view.addSelectionInterval(row, row)
    }
    val lead = kept.leadSelectionIndex
    setLeadAndAnchor(view, if (lead >= 0 && !kept.isSelectionEmpty) toView(lead) else lead)
    view.valueIsAdjusting = false
  }

  private fun editedModelRows(toModel: (Int) -> Int): Map<GridTable, Int> =
    tables.all().filter { it.isEditing }.associateWith { toModel(it.editingRow) }

  private fun cancelEditing(table: GridTable) {
    table.cellEditor?.cancelCellEditing()
    if (table.cellEditor != null) table.removeEditor()
  }

  private fun repaintUpdatedRows(change: RowsChange) {
    for (table in tables.all()) {
      if (change.last - change.first > 10) {
        table.repaint()
        continue
      }
      for (modelRow in change.first..change.last) {
        val row = toView(modelRow)
        if (row < 0) continue
        val cell = table.getCellRect(row, 0, true)
        table.repaint(Rectangle(0, cell.y, table.width, cell.height))
      }
    }
  }

  /** What a table does for a change of every row, done once for the shared row and column selection. */
  private fun clearSelectionAndLeadAnchor() {
    val columns = columnModel.selectionModel
    rows.valueIsAdjusting = true
    columns.valueIsAdjusting = true
    main.clearSelection()
    setLeadAndAnchor(rows, -1)
    setLeadAndAnchor(columns, -1)
    rows.valueIsAdjusting = false
    columns.valueIsAdjusting = false
  }

  /** The selected rows of one data change, in model rows. */
  private class SelectionSnapshot(private val modelRows: IntArray, private val lead: Int) {
    fun restore(view: ListSelectionModel, toView: (Int) -> Int) {
      val rows = IntArray(modelRows.size) { toView(modelRows[it]) }
      if (rows.isEmpty() || rows.size == 1 && rows[0] == view.minSelectionIndex) return
      view.valueIsAdjusting = true
      view.clearSelection()
      for (row in rows) if (row >= 0) view.addSelectionInterval(row, row)
      setLeadAndAnchor(view, toView(lead))
      view.valueIsAdjusting = false
    }

    companion object {
      fun take(view: ListSelectionModel, toModel: (Int) -> Int): SelectionSnapshot {
        val rows = selectedRows(view)
        return SelectionSnapshot(IntArray(rows.size) { toModel(rows[it]) }, toModel(view.leadSelectionIndex))
      }
    }
  }

  /** A data change in model rows, and where an earlier model row is after it. */
  private class RowsChange(event: TableModelEvent, private val modelRowCount: Int) {
    val type: Int = event.type
    val column: Int = event.column
    val allRows: Boolean = event.lastRow == Int.MAX_VALUE
    val first: Int = max(0, event.firstRow)
    val last: Int = if (event.lastRow < 0) max(0, modelRowCount - 1) else event.lastRow
    private val length: Int get() = last - first + 1

    fun modelRowAfter(modelRow: Int): Int = when {
      modelRow < first || type == TableModelEvent.UPDATE -> modelRow
      type == TableModelEvent.INSERT -> modelRow + length
      modelRow <= last -> -1
      else -> modelRow - length
    }.takeIf { it < modelRowCount } ?: -1

    fun moveRows(selection: DefaultListSelectionModel) {
      when (type) {
        TableModelEvent.INSERT -> selection.insertIndexInterval(first, length, true)
        TableModelEvent.DELETE -> selection.removeIndexInterval(first, last)
      }
    }
  }

  private companion object {
    fun selectedRows(selection: ListSelectionModel): IntArray {
      if (selection.isSelectionEmpty) return IntArray(0)
      val min = selection.minSelectionIndex
      val rows = IntArray(selection.maxSelectionIndex - min + 1)
      var count = 0
      for (row in min..selection.maxSelectionIndex) if (selection.isSelectedIndex(row)) rows[count++] = row
      return rows.copyOf(count)
    }

    /** Moves the lead and the anchor to [row] and leaves the selection as it is. */
    fun setLeadAndAnchor(selection: ListSelectionModel, row: Int) {
      if (row < 0) {
        selection.anchorSelectionIndex = -1
        selection.leadSelectionIndex = -1
        return
      }
      if (selection.isSelectedIndex(row)) selection.addSelectionInterval(row, row) else selection.removeSelectionInterval(row, row)
      selection.anchorSelectionIndex = row
    }
  }
}
