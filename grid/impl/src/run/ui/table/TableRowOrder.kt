package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.grid.GridSearchSession
import java.awt.Rectangle
import javax.swing.DefaultListSelectionModel
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.RowSorter
import javax.swing.SortOrder
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
 * Owns the row sorter that both tables share, which follows the grid's sort order and filters. A JTable tells its own sorter of data
 * changes; with one shared sorter, only this class does. After each data change, sort or filter, it puts the row selection and open
 * editors back on their records. Then it applies the row heights again and repaints both tables.
 */
internal class TableRowOrder(
  private val grid: DataGrid,
  /** The view's row selection, which every table shares. */
  private val rowSelection: ListSelectionModel,
  /** The view's column model, whose selection is the grid's column selection. */
  private val columnModel: TableColumnModel,
  private val rowHeights: RowHeightModel,
  /** The view's tables, whose open editors this keeps on their records through a change of the row order. */
  private val tables: ViewTables,
  /** The grid's selection, which converts rows through the sorter as the tables do. */
  private val gridSelectionModel: TableSelectionModel,
) {
  /** The main table, which shows the view's data model. A transpose gives it a new data model and fires its `model` property. */
  private val main: GridTable = tables.main

  private val dataListener = TableModelListener(::dataChanged)
  private val sorterListener = RowSorterListener(::sorterChanged)

  /** The row selection as model rows, kept while a filter hides rows, so a record the filter hid is selected again when it shows. */
  private var keptModelRowSelection: DefaultListSelectionModel? = null
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
      keptModelRowSelection = null
      rowHeights.clear()
      sorter = createSorter()
    }
    rowSelection.addListSelectionListener { if (!restoringSelection) keptModelRowSelection = null }
  }

  fun updateSortKeys() {
    sorter.setSortKeys(sortKeys())
  }

  fun updateRowFilter() {
    sorter.setRowFilter(if (main.isTransposed) TransposedColumnFilter(grid) else RecordFilter(grid, grid.localFilterState))
  }

  /** The keys the sorter sorts by, or null when the grid doesn't sort locally. */
  private fun sortKeys(): List<RowSorter.SortKey>? {
    if (main.isTransposed || grid.isSortViaOrderBy) return null
    return grid.sortOrderMap.values.map { column ->
      RowSorter.SortKey(column.columnNumber, if (grid.getSortOrder(column) < 0) SortOrder.ASCENDING else SortOrder.DESCENDING)
    }
  }

  /** The model's structure changed. Forgets the kept selection, the row heights and the sorter's order, as a table's own sorter would. */
  fun structureChanged() {
    keptModelRowSelection = null
    rowHeights.clear()
    notifySorter { sorter.modelStructureChanged() }
  }

  private fun createSorter(): TableRowSorter<TableModel> {
    val created = GridRowSorter(main.model, grid)
    created.addRowSorterListener(sorterListener)
    for (table in tables.all()) table.setSharedRowSorter(created)
    gridSelectionModel.setRowSorter(created)
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

  /** The sort keys or the filter changed. Rows from before the change convert to model rows through [event]. */
  private fun sorted(event: RowSorterEvent) {
    val toModel = { viewRow: Int ->
      when {
        viewRow < 0 -> -1
        event.previousRowCount == 0 -> viewRow
        else -> event.convertPreviousRowIndexToModel(viewRow)
      }
    }
    val filtering = sorter.viewRowCount != main.model.rowCount
    if (keptModelRowSelection == null && filtering) keptModelRowSelection = selectionInModelRows(toModel)
    val snapshot = if (keptModelRowSelection == null) SelectionSnapshot.take(rowSelection, toModel) else null
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
    val toModel = { viewRow: Int -> if (viewRow < 0 || viewRow >= sorter.viewRowCount) -1 else sorter.convertRowIndexToModel(viewRow) }
    var snapshot: SelectionSnapshot? = null
    when {
      change.allRows -> keptModelRowSelection = null
      keptModelRowSelection != null -> keptModelRowSelection?.let(change::moveRows)
      else -> snapshot = SelectionSnapshot.take(rowSelection, toModel)
    }
    val editedRows = if (change.allRows) tables.all().filter { it.isEditing }.associateWith { -1 } else editedModelRows(toModel)
    when {
      change.allRows -> rowHeights.clear()
      change.eventType == TableModelEvent.INSERT -> rowHeights.rowsInserted(change.firstModelRow, change.lastModelRow)
      change.eventType == TableModelEvent.DELETE -> rowHeights.rowsDeleted(change.firstModelRow, change.lastModelRow)
    }

    notifySorter {
      when {
        change.allRows -> sorter.allRowsChanged()
        change.eventType == TableModelEvent.INSERT -> sorter.rowsInserted(change.firstModelRow, change.lastModelRow)
        change.eventType == TableModelEvent.DELETE -> sorter.rowsDeleted(change.firstModelRow, change.lastModelRow)
        else -> notifyUpdate(change)
      }
    }

    if (change.eventType == TableModelEvent.UPDATE && !change.allRows && !sortedWhileNotifying) {
      repaintUpdatedRows(change)
      return
    }
    rowsReordered(snapshot, editedRows, change::modelRowAfter)
    if (change.allRows) clearSelectionAndLeadAnchor()
  }

  private fun notifyUpdate(change: RowsChange) {
    if (change.modelColumn == TableModelEvent.ALL_COLUMNS) sorter.rowsUpdated(change.firstModelRow, change.lastModelRow)
    else sorter.rowsUpdated(change.firstModelRow, change.lastModelRow, change.modelColumn)
  }

  /**
   * Puts the selection and the editors back on their records, and lays the tables out for the new row order. [editedRows] maps each
   * editing table to its editor's model row, or to -1 when the edit can't follow its record and is canceled.
   */
  private fun rowsReordered(snapshot: SelectionSnapshot?, editedRows: Map<GridTable, Int>, modelRowAfter: (Int) -> Int) {
    val viewRowAfter = { modelRow: Int -> toView(modelRowAfter(modelRow)) }
    restoringSelection = true
    try {
      val kept = keptModelRowSelection
      if (snapshot != null) snapshot.restore(rowSelection, viewRowAfter)
      else if (kept != null) restoreKeptSelection(kept)
    }
    finally {
      restoringSelection = false
    }
    for ((table, modelRow) in editedRows) {
      val row = if (modelRow < 0) -1 else viewRowAfter(modelRow)
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
    val selection = rowSelection
    val kept = DefaultListSelectionModel()
    for (row in selectedRows(selection)) {
      val modelRow = toModel(row)
      if (modelRow >= 0) kept.addSelectionInterval(modelRow, modelRow)
    }
    setLeadAndAnchor(kept, toModel(selection.leadSelectionIndex))
    return kept
  }

  private fun restoreKeptSelection(kept: DefaultListSelectionModel) {
    val selection = rowSelection
    selection.valueIsAdjusting = true
    selection.clearSelection()
    for (modelRow in selectedRows(kept)) {
      val row = toView(modelRow)
      if (row >= 0) selection.addSelectionInterval(row, row)
    }
    val lead = kept.leadSelectionIndex
    setLeadAndAnchor(selection, if (lead >= 0 && !kept.isSelectionEmpty) toView(lead) else lead)
    selection.valueIsAdjusting = false
  }

  private fun editedModelRows(toModel: (Int) -> Int): Map<GridTable, Int> =
    tables.all().filter { it.isEditing }.associateWith { toModel(it.editingRow) }

  private fun cancelEditing(table: GridTable) {
    table.cellEditor?.cancelCellEditing()
    if (table.cellEditor != null) table.removeEditor()
  }

  private fun repaintUpdatedRows(change: RowsChange) {
    for (table in tables.all()) {
      if (change.lastModelRow - change.firstModelRow > 10) {
        table.repaint()
        continue
      }
      for (modelRow in change.firstModelRow..change.lastModelRow) {
        val row = toView(modelRow)
        if (row < 0) continue
        val cell = table.getCellRect(row, 0, true)
        table.repaint(Rectangle(0, cell.y, table.width, cell.height))
      }
    }
  }

  /** Clears the shared row and column selection and their lead and anchor, as a table does when every row changes. */
  private fun clearSelectionAndLeadAnchor() {
    val columnSelection = columnModel.selectionModel
    rowSelection.valueIsAdjusting = true
    columnSelection.valueIsAdjusting = true
    main.clearSelection()
    setLeadAndAnchor(rowSelection, -1)
    setLeadAndAnchor(columnSelection, -1)
    rowSelection.valueIsAdjusting = false
    columnSelection.valueIsAdjusting = false
  }

  /**
   * Sorts the records of [dataModel] with the grid's comparators, which compare whole records. While [dataModel] is transposed, its rows
   * are grid columns, so it sorts nothing; a transpose replaces the model and the sorter at once. It holds the grid for the records and
   * the comparators: [TableRowOrder] creates and replaces it, and the tables see only a Swing `RowSorter`.
   */
  private class GridRowSorter(
    private val dataModel: GridTableModel,
    private val grid: DataGrid,
  ) : TableRowSorter<TableModel>(dataModel) {
    private val transposed = dataModel is TransposedGridTableModel

    init {
      setModelWrapper(object : ModelWrapper<TableModel, Int>() {
        override fun getModel(): TableModel = dataModel
        override fun getColumnCount(): Int = dataModel.columnCount
        override fun getRowCount(): Int = dataModel.rowCount
        override fun getValueAt(modelRow: Int, modelColumn: Int): GridRow? =
          grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getRow(ModelIndex.forRow(grid, modelRow))
        override fun getIdentifier(modelRow: Int): Int = modelRow
      })
      maxSortKeys = 1
      // A changed value moves its record, as with a JBTable's own sorter.
      sortsOnUpdates = true
    }

    override fun toggleSortOrder(modelColumn: Int) {
      // Does nothing: Swing calls this only once for a double click, so the view's TableHeaderClicks sorts on header clicks.
    }

    override fun useToString(modelColumn: Int): Boolean = false

    override fun getComparator(modelColumn: Int): Comparator<*>? = gridComparator(modelColumn) ?: super.getComparator(modelColumn)

    override fun isSortable(modelColumn: Int): Boolean = gridComparator(modelColumn) != null

    private fun gridComparator(modelColumn: Int): Comparator<*>? =
      if (transposed) null else grid.getComparator(ModelIndex.forColumn(grid, modelColumn))
  }

  /** Shows the records that the local filter, a search that filters rows, and the grid let through. */
  private class RecordFilter(private val grid: DataGrid, private val localFilterState: LocalFilterState) : RowFilter<TableModel, Int>() {
    override fun include(entry: Entry<out TableModel, out Int>): Boolean {
      val row = ModelIndex.forRow(grid, entry.identifier)
      return localFilterState.include(grid, row) && matchesSearch(row) && !grid.isRowFilteredOut(row)
    }

    private fun matchesSearch(row: ModelIndex<GridRow>): Boolean {
      @Suppress("UNCHECKED_CAST")
      val search = grid.searchSession as? GridSearchSession<GridRow, GridColumn> ?: return true
      if (!search.isFilteringEnabled || search.findModel.stringToFind.isEmpty() || search.isSearchInProgress) return true
      return grid.visibleColumns.asIterable().any { search.isMatchedCell(row, it) }
    }
  }

  /** Shows, as the rows of a transposed table, the grid columns that the grid shows and doesn't filter out. */
  private class TransposedColumnFilter(private val grid: DataGrid) : RowFilter<TableModel, Int>() {
    override fun include(entry: Entry<out TableModel, out Int>): Boolean {
      val column = ModelIndex.forColumn(grid, entry.identifier)
      return column.isValid(grid) && grid.isColumnEnabled(column) && !grid.isRowFilteredOut(column)
    }
  }

  /** The selected model rows and the lead model row, taken before a change of the row order. */
  private class SelectionSnapshot(private val modelRows: IntArray, private val leadModelRow: Int) {
    fun restore(selection: ListSelectionModel, toView: (Int) -> Int) {
      val rows = IntArray(modelRows.size) { toView(modelRows[it]) }
      if (rows.isEmpty() || rows.size == 1 && rows[0] == selection.minSelectionIndex) return
      selection.valueIsAdjusting = true
      selection.clearSelection()
      for (row in rows) if (row >= 0) selection.addSelectionInterval(row, row)
      setLeadAndAnchor(selection, toView(leadModelRow))
      selection.valueIsAdjusting = false
    }

    companion object {
      fun take(selection: ListSelectionModel, toModel: (Int) -> Int): SelectionSnapshot {
        val rows = selectedRows(selection)
        return SelectionSnapshot(IntArray(rows.size) { toModel(rows[it]) }, toModel(selection.leadSelectionIndex))
      }
    }
  }

  /** A change of the model's rows, which also tells where an earlier model row ends up. */
  private class RowsChange(event: TableModelEvent, private val modelRowCount: Int) {
    val eventType: Int = event.type
    val modelColumn: Int = event.column
    val allRows: Boolean = event.lastRow == Int.MAX_VALUE
    val firstModelRow: Int = max(0, event.firstRow)
    val lastModelRow: Int = if (event.lastRow < 0) max(0, modelRowCount - 1) else event.lastRow
    private val rowCount: Int get() = lastModelRow - firstModelRow + 1

    fun modelRowAfter(modelRow: Int): Int = when {
      modelRow < firstModelRow || eventType == TableModelEvent.UPDATE -> modelRow
      eventType == TableModelEvent.INSERT -> modelRow + rowCount
      modelRow <= lastModelRow -> -1
      else -> modelRow - rowCount
    }.takeIf { it < modelRowCount } ?: -1

    fun moveRows(selection: DefaultListSelectionModel) {
      when (eventType) {
        TableModelEvent.INSERT -> selection.insertIndexInterval(firstModelRow, rowCount, true)
        TableModelEvent.DELETE -> selection.removeIndexInterval(firstModelRow, lastModelRow)
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
