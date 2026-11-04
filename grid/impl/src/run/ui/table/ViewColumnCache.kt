package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.run.ui.DataAccessType.DATA_WITH_MUTATIONS
import com.intellij.openapi.util.Comparing
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap

/**
 * A column object for each model column, hidden ones too, so a column keeps its width while hidden. The cache creates a column when it
 * is first asked for and gives it the view's pins. A column is reused only while the grid still has the same column at its model index.
 * A transposed view's columns are grid rows, and a row with at most one changed value counts as the same.
 */
internal class ViewColumnCache(
  private val grid: DataGrid,
  /** Where each column asks whether it is pinned. */
  private val pins: ColumnPins,
) : Iterable<TableResultViewColumn> {
  private class Entry(
    /** The grid column, or the grid row in a transposed view, that [tableColumn] was created for. */
    val source: Any,
    val tableColumn: TableResultViewColumn,
  )

  private val entriesByModelIndex = Int2ObjectOpenHashMap<Entry>()

  /**
   * Whether the columns are grid rows. [GridColumns] copies it from its data model's type on a transpose, since callers ask for a column
   * by model index alone.
   */
  var transposed: Boolean = false

  /** Whether a column is cached at [modelIndex] and still matches the grid's column or row there. A stale entry is dropped. */
  fun hasCachedColumn(modelIndex: Int): Boolean {
    if (hasValidEntry(modelIndex)) return true
    entriesByModelIndex.remove(modelIndex)
    return false
  }

  fun getOrCreateColumn(modelIndex: Int): TableResultViewColumn {
    var entry = entriesByModelIndex.get(modelIndex)
    if (!hasCachedColumn(modelIndex) || entry == null) {
      entry = createEntry(modelIndex)
      entriesByModelIndex.put(modelIndex, entry)
    }
    return entry.tableColumn
  }

  private fun createEntry(modelIndex: Int): Entry {
    val tableColumn =
      if (transposed) TransposedTableColumn(grid, modelIndex) else RegularTableColumn(grid, gridColumnAt(modelIndex), modelIndex)
    tableColumn.setPins(pins)
    return Entry(getColumnData(modelIndex)!!, tableColumn)
  }

  fun retainColumns(columnsToRetain: Collection<GridColumn>) {
    entriesByModelIndex.values.removeIf { entry -> !(entry.source is GridColumn && entry.source in columnsToRetain) }
  }

  override fun iterator(): Iterator<TableResultViewColumn> = entriesByModelIndex.values.asSequence().map { it.tableColumn }.iterator()

  /**
   * Whether the cached column at [modelIndex] still represents the grid column at that index. In a transposed view a row counts
   * as the same when its number and size match and at most one value differs.
   */
  private fun hasValidEntry(modelIndex: Int): Boolean {
    val entry = entriesByModelIndex.get(modelIndex) ?: return false

    val cachedSource = entry.source
    val currentSource = getColumnData(modelIndex)
    if (Comparing.equal(cachedSource, currentSource)) return true

    // let's attempt to detect if it's the same column data
    //TODO try using primary keys and ROWID columns for it
    if (cachedSource is GridRow && currentSource is GridRow &&
        cachedSource.rowNum == currentSource.rowNum && cachedSource.size == currentSource.size) {
      val mismatchedValuesCount = (0 until cachedSource.size)
        .count { !Comparing.equal(cachedSource.getValue(it), currentSource.getValue(it)) }
      if (mismatchedValuesCount < 2) return true
    }
    return false
  }

  private fun getColumnData(modelIndex: Int): Any? {
    val model = grid.getDataModel(DATA_WITH_MUTATIONS)
    return if (transposed) model.getRow(ModelIndex.forRow(grid, modelIndex))
    else model.getColumn(ModelIndex.forColumn(grid, modelIndex))
  }

  private fun gridColumnAt(modelIndex: Int): GridColumn =
    grid.getDataModel(DATA_WITH_MUTATIONS).getColumn(ModelIndex.forColumn(grid, modelIndex))!!
}
