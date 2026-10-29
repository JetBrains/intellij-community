package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.datagrid.ViewIndex
import com.intellij.database.run.ui.ColumnMove
import com.intellij.database.run.ui.ColumnOrderRestorer
import com.intellij.database.run.ui.DataAccessType
import com.intellij.openapi.Disposable
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableColumn
import kotlin.math.max

/**
 * The grid's columns, which [TableResultView] owns. [columnModel] holds the visible columns in grid order. [columnCache] holds a column for
 * each model column, hidden ones too, so a column keeps its width while it is hidden.
 *
 * Each table shows its share of [columnModel] through a [GridColumnSlice]: the main table the unpinned columns, the pinned table the pinned
 * ones. So one table shows each column object, and the view's column indices are the grid's. The column selection lives in [columnModel].
 * Each column asks [isPinned] whether it is pinned, which answers from the pins the view was last given.
 *
 * It holds no table or view. The view gives it the data model and tells it of a transpose, and it sends its requests to [host]. It converts
 * indices through the grid, as the rest of the grid's code does.
 */
internal class GridColumns(
  private val grid: DataGrid,
  private val host: GridColumnsHost,
  /** The view's data model, which creates the columns; the view gives it a new one on a transpose. */
  private var dataModel: GridTableModel,
) : Disposable, ColumnPins {
  val columnModel: GridTable.MyTableColumnModel = GridTable.MyTableColumnModel()

  /** Whether the view shows the grid transposed, when the columns of [columnModel] are the grid's rows. */
  private var transposed = false

  /** The pinned columns, hidden ones too, as the view was last given them. */
  private var pinnedColumns: Set<ModelIndex<GridColumn>> = emptySet()

  /**
   * The pinned columns that were visible at the last [lockNewlyShownPinnedColumns]; a pinned column outside it is locked when it
   * shows. [resetColumnWidths] puts every pinned column in it, so the column layout keeps them.
   */
  private var shownPinnedColumns: Set<ModelIndex<GridColumn>> = emptySet()

  val columnCache: GridTable.MyTableColumnCache = GridTable.MyTableColumnCache(grid, dataModel, this)

  /** False once the view is disposed, after which it stops driving the column order of the grid. */
  private var drivesColumnOrder = true

  private var untransposedColumnWidths: Int2ObjectMap<ColumnWidthState>? = null

  private class ColumnWidthState(val width: Int, val setByUser: Boolean)

  init {
    // Swing notifies the last added listener first. Added before the main table's slice, this one reports a move once the slice
    // and the table have handled it.
    columnModel.addColumnModelListener(ColumnMoveReporter())
  }

  /**
   * A transposed view pins no column: its columns are the grid's rows. The view drops the pins before it transposes, and this check
   * also keeps pins given while transposed off those rows.
   */
  override fun isPinned(column: TableColumn): Boolean = modelIndexOf(column) in pinnedColumns && !transposed

  private fun modelIndexOf(column: TableColumn): ModelIndex<GridColumn> = ModelIndex.forColumn(grid, column.modelIndex)

  /**
   * Takes the pins the view was given. A column that is no longer pinned, hidden or not, goes back to the column layout at the width it
   * has. Then [lockNewlyShownPinnedColumns] locks every visible pinned one.
   */
  fun setPinnedColumns(columns: Set<ModelIndex<GridColumn>>) {
    val wasPinned = columnCache.filter { it.isPinned }
    pinnedColumns = columns.toSet()
    shownPinnedColumns = emptySet()
    for (column in wasPinned) {
      if (!column.isPinned) unlockWidth(column)
    }
  }

  /** Whether a visible column is pinned, which is when the view shows the pinned table. */
  fun hasVisiblePinnedColumns(): Boolean = visibleColumns().any { it.isPinned }

  /**
   * A pinned column that became visible since the last call keeps the width it has on screen, which the column layout then leaves
   * alone. A rebuild of the columns locks none again.
   */
  fun lockNewlyShownPinnedColumns() {
    val pinned = visibleColumns().filter { it.isPinned }
    for (column in pinned) {
      if (modelIndexOf(column) !in shownPinnedColumns) lockWidth(column)
    }
    shownPinnedColumns = pinned.mapTo(HashSet(), ::modelIndexOf)
  }

  /**
   * Gives every column back to the column layout. A pinned column, hidden or not, stays with the layout until the pins are given again. So
   * Reset View also lays out a pinned column that it shows again.
   */
  fun resetColumnWidths() {
    for (column in columnCache) column.clearWidthSetByUser()
    shownPinnedColumns = pinnedColumns
  }

  /** The columns of [columnModel] in grid order, whichever table shows them. */
  fun visibleColumns(): List<TableResultViewColumn> = List(columnModel.columnCount) { columnModel.getColumn(it) }

  private fun lockWidth(column: TableResultViewColumn) {
    val width = column.frozenStripWidth
    column.setFrozenColumnWidth(width, column.isWidthSetByUser)
    column.width = width
  }

  private fun unlockWidth(column: TableResultViewColumn) {
    val width = column.columnWidth
    if (column.isWidthSetByUser) column.setColumnWidthByUser(width) else column.setColumnWidth(width)
    column.width = width
  }

  override fun dispose() {
    drivesColumnOrder = false
  }

  /** Whether the grid keeps the column order, which the view then asks it to apply, or the columns must go in model order. */
  private fun gridKeepsColumnOrder(): Boolean = drivesColumnOrder && grid is ColumnOrderRestorer

  private fun applyColumnsDisplayOrder(): Boolean = drivesColumnOrder && host.applyColumnsDisplayOrder()

  fun createDefaultColumnsFromModel() {
    host.columnsRemoving()

    columnModel.removeAllColumns()

    val newColumnIndices = IntArrayList()
    for (columnDataIdx in 0 until dataModel.columnCount) {
      val notShownEarlier = !columnCache.hasCachedColumn(columnDataIdx)
      val tableColumn = columnCache.getOrCreateColumn(columnDataIdx)
      val collapseManager = grid.hierarchicalColumnsCollapseManager
      // prevents the columnsShown call in setColumnEnabled
      val enabled = transposed ||
                    grid.isColumnEnabled(ModelIndex.forColumn(grid, columnDataIdx)) &&
                    !(collapseManager != null &&
                      collapseManager.isColumnHiddenDueToCollapse(ModelIndex.forColumn(grid, columnDataIdx)))
      if (enabled) {
        columnModel.addColumn(tableColumn)
        if (notShownEarlier) {
          newColumnIndices.add(columnDataIdx)
        }
      }
    }

    applyColumnsDisplayOrder()
    if (!newColumnIndices.isEmpty) host.columnsShown(modelIndexSet(newColumnIndices.toIntArray()))
  }

  /** The model indices of the columns of [columnModel], which are grid rows when transposed. */
  private fun modelIndexSet(columns: IntArray): ModelIndexSet<*> =
    if (transposed) ModelIndexSet.forRows(grid, *columns) else ModelIndexSet.forColumns(grid, *columns)

  fun setColumnEnabled(columnIdx: ModelIndex<GridColumn>, enabled: Boolean) {
    if (transposed) {
      dataModel.fireTableDataChanged()
    }
    else {
      setViewColumnVisible(columnIdx, enabled, restoreOrder = true)
    }
  }

  fun setColumnsEnabled(columns: List<ModelIndex<GridColumn>>, enabled: Boolean) {
    if (columns.isEmpty()) return
    if (transposed) {
      dataModel.fireTableDataChanged()
      return
    }
    val deferOrder = enabled && gridKeepsColumnOrder()
    for (column in columns) setViewColumnVisible(column, enabled, restoreOrder = !deferOrder)
    if (deferOrder) applyColumnsDisplayOrder()
  }

  fun setRowEnabled(rowIdx: ModelIndex<GridRow>, enabled: Boolean) {
    if (transposed) {
      setViewColumnVisible(rowIdx, enabled, restoreOrder = true)
    }
    else {
      dataModel.fireTableDataChanged()
    }
  }

  private fun setViewColumnVisible(modelColumnIdx: ModelIndex<*>, visible: Boolean, restoreOrder: Boolean) {
    val viewColumnIdx = modelColumnIdx.toView(grid)
    if (visible && viewColumnIdx.asInteger() < 0) {
      val firstTimeShown = !columnCache.hasCachedColumn(modelColumnIdx.asInteger())
      addColumnAndMoveToTheCorrectPosition(modelColumnIdx, restoreOrder)
      if (firstTimeShown) host.columnsShown(modelIndexSet(intArrayOf(modelColumnIdx.asInteger())))
    }
    else if (!visible && viewColumnIdx.asInteger() >= 0) {
      removeViewColumnFromColumnModel(viewColumnIdx)
    }
  }

  private fun addColumnAndMoveToTheCorrectPosition(modelColumnIdx: ModelIndex<*>, restoreOrder: Boolean) {
    columnModel.addColumn(columnCache.getOrCreateColumn(modelColumnIdx.asInteger()))

    if (!restoreOrder || applyColumnsDisplayOrder()) return
    val lastColumnIndex = columnModel.columnCount - 1
    grid.runWithIgnoreSelectionChanges {
      for (viewTargetColumnIdx in 0 until lastColumnIndex) {
        if (columnModel.getColumn(viewTargetColumnIdx).modelIndex > modelColumnIdx.asInteger()) {
          columnModel.moveColumn(lastColumnIndex, viewTargetColumnIdx)
          break
        }
      }
    }
  }

  private fun removeViewColumnFromColumnModel(viewColumnIdx: ViewIndex<*>) {
    host.columnsRemoving() // a workaround for JDK-6586009
    grid.runWithIgnoreSelectionChanges { columnModel.removeColumn(columnModel.getColumn(viewColumnIdx.asInteger())) }
  }

  /** Drops the cached columns of removed grid columns and rebuilds the columns. */
  fun columnAttributesUpdated() {
    val columns = grid.getDataModel(DataAccessType.DATABASE_DATA).columns
    if (!transposed) {
      columnCache.retainColumns(columns)
      createDefaultColumnsFromModel()
    }
  }

  fun restoreColumnsOrder(expectedToModel: Map<Int, ModelIndex<GridColumn>>) {
    if (transposed) return
    for (expectedPos in 0 until columnModel.columnCount) {
      val modelIndex = expectedToModel[expectedPos] ?: continue
      // The grid's converter caches the positions until a column is added, removed or moved.
      val actualPos = modelIndex.toView(grid).asInteger()
      if (actualPos != -1 && actualPos != expectedPos) columnModel.moveColumn(actualPos, expectedPos)
    }
  }

  /** The cached column of [column] while the view shows it, otherwise null. */
  fun getLayoutColumn(column: ModelIndex<*>): TableResultViewColumn? =
    if (column.toView(grid).asInteger() != -1) columnCache.getOrCreateColumn(column.asInteger()) else null

  /**
   * The view is about to show [model], the new data model of a transpose, in the main table. The cached columns belong to the old
   * model, so they go; the widths of the untransposed columns are kept for the way back. Then the view shows the model in the main
   * table and has the columns rebuilt; on the way back, [restoreUntransposedColumnWidths] brings the widths back.
   */
  fun startTranspose(transposed: Boolean, model: GridTableModel) {
    if (transposed) rememberUntransposedColumnWidths()
    this.transposed = transposed
    dataModel = model
    columnCache.setTableModel(model)
    columnCache.retainColumns(emptyList())
  }

  private fun rememberUntransposedColumnWidths() {
    val widths = Int2ObjectOpenHashMap<ColumnWidthState>()
    untransposedColumnWidths = widths
    for (i in 0 until columnModel.columnCount) {
      val column = columnModel.getColumn(i)
      val width = max(column.width, column.columnWidth)
      widths.put(column.modelIndex, ColumnWidthState(width, column.isWidthSetByUser))
    }
  }

  /** Gives the columns back the widths they had before the view was transposed. */
  fun restoreUntransposedColumnWidths() {
    val widths = untransposedColumnWidths ?: return
    for (entry in widths.int2ObjectEntrySet()) {
      val column = getLayoutColumn(ModelIndex.forColumn(grid, entry.intKey)) ?: continue
      val state = entry.value
      if (state.setByUser) {
        column.setColumnWidthByUser(state.width)
      }
      else {
        column.setColumnWidth(state.width)
      }
      column.width = state.width
    }
    untransposedColumnWidths = null
  }

  /** Tells the grid, which keeps the complete column order, about a move of a column in [columnModel]. */
  private inner class ColumnMoveReporter : TableColumnModelListener {
    override fun columnMoved(e: TableColumnModelEvent) {
      if (!gridKeepsColumnOrder() || e.fromIndex == e.toIndex) return
      val target = e.toIndex + if (e.fromIndex < e.toIndex) -1 else 1
      val column = ModelIndex.forColumn(grid, columnModel.getColumn(e.toIndex).modelIndex)
      val neighbor = ModelIndex.forColumn(grid, columnModel.getColumn(target).modelIndex)
      host.columnMoved(ColumnMove(column, neighbor, e.fromIndex > e.toIndex))
    }

    override fun columnAdded(e: TableColumnModelEvent) = Unit
    override fun columnRemoved(e: TableColumnModelEvent) = Unit
    override fun columnMarginChanged(e: ChangeEvent) = Unit
    override fun columnSelectionChanged(e: ListSelectionEvent) = Unit
  }
}
