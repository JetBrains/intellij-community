package com.intellij.database.run.ui.table

import org.jetbrains.annotations.ApiStatus
import java.util.Collections
import java.util.Enumeration
import javax.swing.DefaultListSelectionModel
import javax.swing.ListSelectionModel
import javax.swing.event.ChangeEvent
import javax.swing.event.EventListenerList
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

/** Which columns of the view's master column model a [GridColumnSlice] shows: those of the pinned table, or those of the main table. */
@ApiStatus.Internal
enum class ColumnsToShow { PINNED, UNPINNED }

/**
 * A filtered window onto the view's column model, "the master". It gives one table its subset of the columns; the order, selection and
 * widths stay in the master. The pinned table's slice shows the pinned columns, the main table's the unpinned ones, both in master order;
 * [columnsToShow] and [pins] decide which.
 *
 * A slice copies no column. It translates a table's local index to a master index and back. A read returns the master's column object;
 * a move, a resize or a selection change goes to the master. The master's events come back to each table in its own indices.
 *
 * The master moves a column by removing it and inserting it again, with selection events in between. The slice caches its columns and
 * updates the cache on each master event. So a table always reads the columns it was last told about, and gets the move's selection
 * events only after the move completes.
 *
 * The grid's selection listeners don't listen to a slice. The master reports the start and the end of each change, and they hear of it at
 * the end, after both tables have updated.
 */
@ApiStatus.Internal
class GridColumnSlice(
  private val masterColumns: TableColumnModel,
  private val pins: ColumnPins,
  private val columnsToShow: ColumnsToShow,
  /** The view's columns in displayed order, which an extend from the other table's anchor follows. */
  private val displayedColumns: DisplayedColumns,
) : TableColumnModel {
  private val listeners = EventListenerList()
  private val columns = mutableListOf<TableColumn>()

  /** The master index of each column in [columns], ascending. */
  private val masterIndices = mutableListOf<Int>()
  private val selection: GridColumnSliceSelection

  private val masterListener = object : TableColumnModelListener {
    override fun columnAdded(e: TableColumnModelEvent) {
      shiftMasterIndices(fromMasterIndex = e.toIndex, delta = 1)
      val column = masterColumns.getColumn(e.toIndex)
      if (!shows(column)) return
      val localIndex = insert(e.toIndex, column)
      fire { it.columnAdded(TableColumnModelEvent(this@GridColumnSlice, 0, localIndex)) }
    }

    override fun columnRemoved(e: TableColumnModelEvent) {
      val localIndex = toLocal(e.fromIndex)
      if (localIndex >= 0) {
        columns.removeAt(localIndex)
        masterIndices.removeAt(localIndex)
      }
      shiftMasterIndices(fromMasterIndex = e.fromIndex + 1, delta = -1)
      if (localIndex >= 0) fire { it.columnRemoved(TableColumnModelEvent(this@GridColumnSlice, localIndex, 0)) }
    }

    override fun columnMoved(e: TableColumnModelEvent) {
      val fromLocalIndex = masterIndices.indexOf(e.fromIndex)
      if (e.fromIndex != e.toIndex) applyMasterMove(e.fromIndex, e.toIndex)
      // The master fires the move after its selection changes, so the table hears of them in the same order.
      selection.catchUp()
      // A move of a column this slice doesn't show leaves its order as it is.
      if (fromLocalIndex < 0) return
      val toLocalIndex = masterIndices.indexOf(e.toIndex)
      fire { it.columnMoved(TableColumnModelEvent(this@GridColumnSlice, fromLocalIndex, toLocalIndex)) }
    }

    override fun columnMarginChanged(e: ChangeEvent) {
      fire { it.columnMarginChanged(ChangeEvent(this@GridColumnSlice)) }
    }

    override fun columnSelectionChanged(e: ListSelectionEvent) = Unit
  }

  init {
    for (masterIndex in 0 until masterColumns.columnCount) {
      val column = masterColumns.getColumn(masterIndex)
      if (shows(column)) {
        columns.add(column)
        masterIndices.add(masterIndex)
      }
    }
    selection = GridColumnSliceSelection(this, masterColumns.selectionModel)
    masterColumns.addColumnModelListener(masterListener)
    selection.addListSelectionListener(ListSelectionListener { e -> fire { it.columnSelectionChanged(e) } })
  }

  /** Stops following the master, which outlives a table that is dropped. */
  fun dispose() {
    masterColumns.removeColumnModelListener(masterListener)
    selection.dispose()
  }

  /**
   * The pins changed, so the slice checks every column again. The columns that leave it are removed one at a time, then those that join it
   * are added. So each event describes the slice as it is when the event arrives.
   */
  fun pinsChanged() {
    for (localIndex in columns.indices.reversed()) {
      if (shows(columns[localIndex])) continue
      columns.removeAt(localIndex)
      masterIndices.removeAt(localIndex)
      fire { it.columnRemoved(TableColumnModelEvent(this, localIndex, 0)) }
    }
    for (masterIndex in 0 until masterColumns.columnCount) {
      val column = masterColumns.getColumn(masterIndex)
      if (!shows(column) || toLocal(masterIndex) >= 0) continue
      val localIndex = insert(masterIndex, column)
      fire { it.columnAdded(TableColumnModelEvent(this, 0, localIndex)) }
    }
  }

  /**
   * The master index of the column at [localIndex] in this slice, or -1. Local and master indices stay `Int`: they are Swing positions in a
   * column model, and in a transposed view the columns are the grid's rows.
   */
  fun toMaster(localIndex: Int): Int = masterIndices.getOrElse(localIndex) { -1 }

  /** The index in this slice of the master column at [masterIndex], or -1 when this slice doesn't show it. Indices as in [toMaster]. */
  fun toLocal(masterIndex: Int): Int = if (masterIndex < 0) -1 else masterIndices.binarySearch(masterIndex).takeIf { it >= 0 } ?: -1

  /** Whether [masterIndex] is a column of the master that this slice doesn't show. */
  internal fun omits(masterIndex: Int): Boolean = masterIndex in 0 until masterColumns.columnCount && toLocal(masterIndex) < 0

  /** Whether the anchor of the master's column selection is a column this slice doesn't show. This slice's selection reads it as -1. */
  fun omitsAnchor(): Boolean = omits(masterColumns.selectionModel.anchorSelectionIndex)

  /** Whether the anchor column of the master's selection is selected, also when the other slice shows it. */
  fun isAnchorColumnSelected(): Boolean = masterColumns.selectionModel.let { it.isSelectedIndex(it.anchorSelectionIndex) }

  /**
   * Extends the master's column selection from its anchor to the column at [localIndex], in displayed order, as [change] says. Swing's
   * own extend starts from this slice's first column when the slice omits the anchor.
   */
  fun extendColumnSelection(localIndex: Int, change: RangeChange) {
    displayedColumns.select(masterColumns.selectionModel.anchorSelectionIndex, toMaster(localIndex), change)
  }

  /** Whether each cached column is at its master index, which it is except while the master is in the middle of a move. */
  internal fun isInStepWithMaster(): Boolean {
    val count = masterColumns.columnCount
    return masterIndices.indices.all { masterIndices[it] < count && masterColumns.getColumn(masterIndices[it]) === columns[it] }
  }

  private fun shows(column: TableColumn): Boolean = when (columnsToShow) {
    ColumnsToShow.PINNED -> pins.isPinned(column)
    ColumnsToShow.UNPINNED -> !pins.isPinned(column)
  }

  /** Adds [column] among the cached columns in master order, and returns its local index. */
  private fun insert(masterIndex: Int, column: TableColumn): Int {
    val localIndex = -(masterIndices.binarySearch(masterIndex) + 1)
    columns.add(localIndex, column)
    masterIndices.add(localIndex, masterIndex)
    return localIndex
  }

  /**
   * Shifts the cached master indices from [fromMasterIndex] on by [delta], after the master added or removed a column there. The indices
   * ascend, so a rebuild that adds or removes at the end shifts none.
   */
  private fun shiftMasterIndices(fromMasterIndex: Int, delta: Int) {
    val found = masterIndices.binarySearch(fromMasterIndex)
    for (localIndex in (if (found >= 0) found else -(found + 1)) until masterIndices.size) masterIndices[localIndex] += delta
  }

  /** The master moved the column at [fromMasterIndex] to [toMasterIndex]; the cache follows, shifting the columns between by one. */
  private fun applyMasterMove(fromMasterIndex: Int, toMasterIndex: Int) {
    val movedLocalIndex = masterIndices.indexOf(fromMasterIndex)
    val column = if (movedLocalIndex >= 0) columns.removeAt(movedLocalIndex) else null
    if (movedLocalIndex >= 0) masterIndices.removeAt(movedLocalIndex)
    for (localIndex in masterIndices.indices) {
      val masterIndex = masterIndices[localIndex]
      masterIndices[localIndex] = when {
        fromMasterIndex < toMasterIndex && masterIndex in fromMasterIndex + 1..toMasterIndex -> masterIndex - 1
        fromMasterIndex > toMasterIndex && masterIndex in toMasterIndex until fromMasterIndex -> masterIndex + 1
        else -> masterIndex
      }
    }
    if (column != null) insert(toMasterIndex, column)
  }

  private fun fire(notification: (TableColumnModelListener) -> Unit) {
    listeners.getListeners(TableColumnModelListener::class.java).reversed().forEach(notification)
  }

  override fun addColumn(column: TableColumn) {
    masterColumns.addColumn(column)
  }

  override fun removeColumn(column: TableColumn) {
    masterColumns.removeColumn(column)
  }

  override fun moveColumn(columnIndex: Int, newIndex: Int) {
    masterColumns.moveColumn(toMaster(columnIndex), toMaster(newIndex))
  }

  override fun setColumnMargin(newMargin: Int) {
    masterColumns.columnMargin = newMargin
  }

  override fun getColumnCount(): Int = columns.size

  override fun getColumns(): Enumeration<TableColumn> = Collections.enumeration(columns.toList())

  override fun getColumnIndex(columnIdentifier: Any): Int {
    val localIndex = columns.indexOfFirst { it.identifier == columnIdentifier }
    require(localIndex >= 0) { "Identifier not found" }
    return localIndex
  }

  override fun getColumn(columnIndex: Int): TableColumn = columns[columnIndex]

  override fun getColumnMargin(): Int = masterColumns.columnMargin

  override fun getColumnIndexAtX(xPosition: Int): Int {
    if (xPosition < 0) return -1
    var x = xPosition
    for (localIndex in columns.indices) {
      x -= columns[localIndex].width
      if (x < 0) return localIndex
    }
    return -1
  }

  override fun getTotalColumnWidth(): Int = columns.sumOf { it.width }

  override fun setSelectionModel(newModel: ListSelectionModel): Unit =
    throw UnsupportedOperationException("A slice selects through the master's selection model")

  override fun getSelectionModel(): ListSelectionModel = selection

  override fun setColumnSelectionAllowed(allowed: Boolean) {
    masterColumns.columnSelectionAllowed = allowed
  }

  override fun getColumnSelectionAllowed(): Boolean = masterColumns.columnSelectionAllowed

  override fun getSelectedColumns(): IntArray = columns.indices.filter(selection::isSelectedIndex).toIntArray()

  override fun getSelectedColumnCount(): Int = columns.indices.count(selection::isSelectedIndex)

  override fun addColumnModelListener(listener: TableColumnModelListener) {
    listeners.add(TableColumnModelListener::class.java, listener)
  }

  override fun removeColumnModelListener(listener: TableColumnModelListener) {
    listeners.remove(TableColumnModelListener::class.java, listener)
  }
}

/**
 * The column selection of a [GridColumnSlice], in the slice's own indices. It reads and writes the master's selection, and an index the
 * slice doesn't show reads as -1. The class extends [DefaultListSelectionModel] because Swing's actions that move the lead alone check for
 * it. `insertIndexInterval` and `removeIndexInterval` do nothing: only a `DefaultTableColumnModel` calls them, and the master adjusts its
 * own selection. The grid's multiple-interval selection is assumed.
 */
private class GridColumnSliceSelection(
  private val slice: GridColumnSlice,
  private val masterSelection: ListSelectionModel,
) : DefaultListSelectionModel() {
  /** Whether the master's selection changed while the slice was out of step with the master. */
  private var changedOutOfStep = false

  private val masterListener = ListSelectionListener { e ->
    // Mid-move, the cached columns don't match the master's selection yet, so the change waits for the end of the move.
    if (!slice.isInStepWithMaster()) {
      changedOutOfStep = true
      return@ListSelectionListener
    }
    val first = (e.firstIndex..e.lastIndex).firstNotNullOfOrNull { slice.toLocal(it).takeIf { local -> local >= 0 } }
    val last = (e.lastIndex downTo e.firstIndex).firstNotNullOfOrNull { slice.toLocal(it).takeIf { local -> local >= 0 } }
    if (first != null && last != null) fireValueChanged(first, last, e.valueIsAdjusting)
  }

  init {
    masterSelection.addListSelectionListener(masterListener)
  }

  fun dispose() {
    masterSelection.removeListSelectionListener(masterListener)
  }

  /** Tells of the selection changes that came while the slice was out of step, now that its cache matches the master again. */
  fun catchUp() {
    if (!changedOutOfStep) return
    changedOutOfStep = false
    if (slice.columnCount > 0) fireValueChanged(0, slice.columnCount - 1, masterSelection.valueIsAdjusting)
  }

  override fun isSelectedIndex(index: Int): Boolean = slice.toMaster(index).let { it >= 0 && masterSelection.isSelectedIndex(it) }

  override fun getMinSelectionIndex(): Int = (0 until slice.columnCount).firstOrNull(::isSelectedIndex) ?: -1

  override fun getMaxSelectionIndex(): Int = (slice.columnCount - 1 downTo 0).firstOrNull(::isSelectedIndex) ?: -1

  override fun isSelectionEmpty(): Boolean = minSelectionIndex < 0

  override fun getSelectedIndices(): IntArray = (0 until slice.columnCount).filter(::isSelectedIndex).toIntArray()

  override fun getSelectedItemsCount(): Int = (0 until slice.columnCount).count(::isSelectedIndex)

  override fun getAnchorSelectionIndex(): Int = slice.toLocal(masterSelection.anchorSelectionIndex)

  override fun getLeadSelectionIndex(): Int = slice.toLocal(masterSelection.leadSelectionIndex)

  /** Writing back an anchor read as -1 leaves the master's anchor in the other table as it is. */
  override fun setAnchorSelectionIndex(anchorIndex: Int) {
    if (anchorIndex < 0 && slice.omits(masterSelection.anchorSelectionIndex)) return
    masterSelection.anchorSelectionIndex = slice.toMaster(anchorIndex)
  }

  /** Writing back a lead read as -1 leaves the master's lead in the other table as it is. */
  override fun setLeadSelectionIndex(leadIndex: Int) {
    if (leadIndex < 0 && slice.omits(masterSelection.leadSelectionIndex)) return
    masterSelection.leadSelectionIndex = slice.toMaster(leadIndex)
  }

  override fun moveLeadSelectionIndex(leadIndex: Int) {
    if (leadIndex < 0 && slice.omits(masterSelection.leadSelectionIndex)) return
    val masterLead = slice.toMaster(leadIndex)
    if (masterSelection is DefaultListSelectionModel) masterSelection.moveLeadSelectionIndex(masterLead)
    else masterSelection.leadSelectionIndex = masterLead
  }

  override fun setSelectionInterval(index0: Int, index1: Int) {
    changeSelection(index0, index1, clearFirst = true, select = true)
  }

  override fun addSelectionInterval(index0: Int, index1: Int) {
    changeSelection(index0, index1, clearFirst = false, select = true)
  }

  override fun removeSelectionInterval(index0: Int, index1: Int) {
    changeSelection(index0, index1, clearFirst = false, select = false)
  }

  override fun clearSelection() {
    masterSelection.clearSelection()
  }

  override fun insertIndexInterval(index: Int, length: Int, before: Boolean) {
  }

  override fun removeIndexInterval(index0: Int, index1: Int) {
  }

  override fun getSelectionMode(): Int = masterSelection.selectionMode

  override fun setSelectionMode(selectionMode: Int) {
    masterSelection.selectionMode = selectionMode
  }

  override fun getValueIsAdjusting(): Boolean = masterSelection.valueIsAdjusting

  override fun setValueIsAdjusting(isAdjusting: Boolean) {
    masterSelection.valueIsAdjusting = isAdjusting
  }

  /**
   * Applies the local interval [index0]..[index1] to the master's selection. A local interval can span the other table's columns, so it
   * becomes one master interval per run of this slice's columns, in one adjusting change. A single run is one master interval. The anchor
   * and the lead end on the master columns of [index0] and [index1].
   */
  private fun changeSelection(index0: Int, index1: Int, clearFirst: Boolean, select: Boolean) {
    if (index0 < 0 || index1 < 0) return
    val runs = masterRuns(minOf(index0, index1)..maxOf(index0, index1))
    val master0 = slice.toMaster(index0)
    val master1 = slice.toMaster(index1)
    if (runs.size == 1 && master0 >= 0 && master1 >= 0) {
      when {
        clearFirst -> masterSelection.setSelectionInterval(master0, master1)
        select -> masterSelection.addSelectionInterval(master0, master1)
        else -> masterSelection.removeSelectionInterval(master0, master1)
      }
      return
    }
    val wasAdjusting = masterSelection.valueIsAdjusting
    masterSelection.valueIsAdjusting = true
    try {
      if (clearFirst) masterSelection.clearSelection()
      for (run in runs) {
        if (select) masterSelection.addSelectionInterval(run.first, run.last)
        else masterSelection.removeSelectionInterval(run.first, run.last)
      }
      masterSelection.anchorSelectionIndex = master0
      moveLeadSelectionIndex(index1)
    }
    finally {
      masterSelection.valueIsAdjusting = wasAdjusting
    }
  }

  /** The master indices of [localRange], as runs of adjacent master indices. */
  private fun masterRuns(localRange: IntRange): List<IntRange> = adjacentRuns(localRange.map(slice::toMaster).filter { it >= 0 })
}
