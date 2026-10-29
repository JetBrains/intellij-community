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

/** Which columns of the view's master column model a [GridColumnSlice] shows. */
@ApiStatus.Internal
enum class ColumnsToShow { ALL, PINNED, UNPINNED }

/**
 * The columns one table shows: the columns of the view's master column model that [columnsToShow] names, by the [pins] it is
 * given, in master order. A slice copies no column and knows no table or view. A move or a resize goes to the master, and the
 * master's events reach the table in the slice's own indices. Its selection model maps the same way to the master's.
 *
 * The slice caches its columns and their master indices, and updates the cache on each master event. A table reads the columns from the
 * cache, so they match the column count it was told, even in the middle of a master change. The master moves a column by removing it and
 * inserting it again, and fires selection events in between. Those reach the table once the move is over, when the cached columns are where
 * the master's selection has them again.
 *
 * The indices stay `int`: they are Swing positions in a column model, and in a transposed view the columns are the grid's rows.
 */
@ApiStatus.Internal
class GridColumnSlice(
  private val masterColumns: TableColumnModel,
  private val pins: ColumnPins,
  private val columnsToShow: ColumnsToShow,
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
      if (e.fromIndex != e.toIndex) moveMasterIndex(e.fromIndex, e.toIndex)
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

  /** The master index of the column at [localIndex] in this slice, or -1. */
  fun toMaster(localIndex: Int): Int = masterIndices.getOrElse(localIndex) { -1 }

  /** The index in this slice of the master column at [masterIndex], or -1 when this slice doesn't show it. */
  fun toLocal(masterIndex: Int): Int = if (masterIndex < 0) -1 else masterIndices.binarySearch(masterIndex).takeIf { it >= 0 } ?: -1

  /** Whether [masterIndex] is a column of the master that this slice doesn't show. */
  internal fun isOutside(masterIndex: Int): Boolean = masterIndex in 0 until masterColumns.columnCount && toLocal(masterIndex) < 0

  /** Whether each cached column is at its master index, which it is except while the master is in the middle of a move. */
  internal fun isInStep(): Boolean {
    val count = masterColumns.columnCount
    return masterIndices.indices.all { masterIndices[it] < count && masterColumns.getColumn(masterIndices[it]) === columns[it] }
  }

  private fun shows(column: TableColumn): Boolean = when (columnsToShow) {
    ColumnsToShow.ALL -> true
    ColumnsToShow.PINNED -> pins.isPinned(column)
    ColumnsToShow.UNPINNED -> !pins.isPinned(column)
  }

  private fun insert(masterIndex: Int, column: TableColumn): Int {
    val localIndex = -(masterIndices.binarySearch(masterIndex) + 1)
    columns.add(localIndex, column)
    masterIndices.add(localIndex, masterIndex)
    return localIndex
  }

  /** The indices ascend, so only the tail from [fromMasterIndex] on shifts, and a rebuild that adds or removes at the end shifts none. */
  private fun shiftMasterIndices(fromMasterIndex: Int, delta: Int) {
    val found = masterIndices.binarySearch(fromMasterIndex)
    for (localIndex in (if (found >= 0) found else -(found + 1)) until masterIndices.size) masterIndices[localIndex] += delta
  }

  /** The master moved the column at [fromMasterIndex] to [toMasterIndex], and the columns between them one place the other way. */
  private fun moveMasterIndex(fromMasterIndex: Int, toMasterIndex: Int) {
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
 * The selection model of a [GridColumnSlice]: the master's column selection in the slice's own indices. It keeps nothing of its own and
 * maps every call; an index the slice doesn't show reads as -1. It extends [DefaultListSelectionModel] because Swing's actions that move
 * the lead alone check for it. It does nothing on `insertIndexInterval` and `removeIndexInterval`, which only a `DefaultTableColumnModel`
 * calls: the master adjusts its own selection. It assumes the grid's multiple-interval selection.
 */
private class GridColumnSliceSelection(
  private val slice: GridColumnSlice,
  private val masterSelection: ListSelectionModel,
) : DefaultListSelectionModel() {
  /** Whether the master's selection changed while the slice was out of step with the master. */
  private var changedOutOfStep = false

  private val masterListener = ListSelectionListener { e ->
    // Mid-move, the cached columns don't match the master's selection yet, so the change waits for the end of the move.
    if (!slice.isInStep()) {
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
    if (anchorIndex < 0 && slice.isOutside(masterSelection.anchorSelectionIndex)) return
    masterSelection.anchorSelectionIndex = slice.toMaster(anchorIndex)
  }

  /** Writing back a lead read as -1 leaves the master's lead in the other table as it is. */
  override fun setLeadSelectionIndex(leadIndex: Int) {
    if (leadIndex < 0 && slice.isOutside(masterSelection.leadSelectionIndex)) return
    masterSelection.leadSelectionIndex = slice.toMaster(leadIndex)
  }

  override fun moveLeadSelectionIndex(leadIndex: Int) {
    if (leadIndex < 0 && slice.isOutside(masterSelection.leadSelectionIndex)) return
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
   * A local interval can span columns of the other table. So it becomes one master interval per run of this slice's columns, inside
   * one adjusting change. The anchor and the lead end on the master columns of [index0] and [index1], as for one interval. An
   * interval of one run is one master interval, which the master changes in one step, as for a table on the master itself.
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

  /** The master indices of [localRange], as runs of consecutive master indices. */
  private fun masterRuns(localRange: IntRange): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = -1
    var previous = -1
    for (localIndex in localRange) {
      val masterIndex = slice.toMaster(localIndex)
      if (masterIndex < 0) continue
      if (start >= 0 && masterIndex == previous + 1) {
        previous = masterIndex
        continue
      }
      if (start >= 0) runs.add(start..previous)
      start = masterIndex
      previous = masterIndex
    }
    if (start >= 0) runs.add(start..previous)
    return runs
  }
}
