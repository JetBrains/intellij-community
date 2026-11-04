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
 * The view's columns, kept outside the tables so that both share one set. [columnModel] holds the visible columns in grid order, pinned
 * ones too, and each table shows its columns through a [GridColumnSlice]. [columnCache] keeps a column for each model column, so a hidden
 * column keeps its width.
 *
 * It fills [columnModel] from the data model and shows and hides columns. It reports column moves to the grid when the grid keeps the
 * column order.
 * It keeps the pins the view last set, and each column reads them through [isPinned]. The column layout, the grid's `GridColumnLayout`,
 * sizes every column whose width is not locked.
 *
 * It holds no view or table, and reaches the view through [host]. On a transpose the view gives it a new data model, whose columns are
 * grid rows.
 */
internal class GridColumns(
  private val grid: DataGrid,
  private val host: GridColumnsHost,
  /**
   * The view's data model: it gives the column count and refreshes the tables when rows show or hide. The view gives a new one on a
   * transpose.
   */
  private var dataModel: GridTableModel,
) : Disposable, ColumnPins {
  val columnModel: ViewColumnModel = ViewColumnModel()

  /** The pinned columns, hidden ones too, as the view was last given them. */
  private var pinnedColumns: Set<ModelIndex<GridColumn>> = emptySet()

  /**
   * Pinned columns that [lockNewlyShownPinnedColumns] leaves alone when they show: those visible at its last call. After
   * [resetColumnWidths], every pinned column too, so the column layout keeps sizing them.
   */
  private var pinnedColumnsNotToLock: Set<ModelIndex<GridColumn>> = emptySet()

  val columnCache: ViewColumnCache = ViewColumnCache(grid, this)

  /** Whether the view shows the grid transposed, when the columns of [columnModel] are the grid's rows. */
  private val transposed: Boolean get() = dataModel is TransposedGridTableModel

  /** True once the view is disposed, after which it stops driving the column order of the grid. */
  private var disposed = false

  /** The widths the columns had before the view was transposed, by model index; null when none are saved. */
  private var untransposedColumnWidths: Int2ObjectMap<ColumnWidthState>? = null

  /** A saved column width, and whether the user set it. */
  private class ColumnWidthState(val width: Int, val setByUser: Boolean)

  init {
    // Swing notifies the last added listener first. Added before the main table's slice, this one reports a move once the slice
    // and the table have handled it.
    columnModel.addColumnModelListener(ColumnMoveReporter())
  }

  /**
   * Whether [column] is pinned. A transposed view pins nothing, as its columns are grid rows, so pins given while transposed don't count.
   */
  override fun isPinned(column: TableColumn): Boolean = modelIndexOf(column) in pinnedColumns && !transposed

  private fun modelIndexOf(column: TableColumn): ModelIndex<GridColumn> = ModelIndex.forColumn(grid, column.modelIndex)

  /**
   * Sets the pinned columns to [columns]. A column that is no longer pinned, hidden or not, returns to the column layout at its current
   * width. The next [lockNewlyShownPinnedColumns] locks every visible pinned column.
   */
  fun setPinnedColumns(columns: Set<ModelIndex<GridColumn>>) {
    val wasPinned = columnCache.filter { it.isPinned }
    pinnedColumns = columns.toSet()
    pinnedColumnsNotToLock = emptySet()
    for (column in wasPinned) {
      if (!column.isPinned) unlockWidth(column)
    }
  }

  /** Whether a visible column is pinned, which is when the view shows the pinned table. */
  fun hasVisiblePinnedColumns(): Boolean = visibleColumns().any { it.isPinned }

  /**
   * A pinned column that became visible since the last call keeps the width it has on screen, which the column layout then leaves
   * alone. Pinned columns already visible at the last call stay as they are, so a rebuild locks nothing new.
   */
  fun lockNewlyShownPinnedColumns() {
    val pinned = visibleColumns().filter { it.isPinned }
    for (column in pinned) {
      if (modelIndexOf(column) !in pinnedColumnsNotToLock) lockWidth(column)
    }
    pinnedColumnsNotToLock = pinned.mapTo(HashSet(), ::modelIndexOf)
  }

  /**
   * Gives every column back to the column layout. Pinned columns, hidden ones too, stay with the layout until the pins are set again.
   * This way the layout also sizes a pinned column that Reset View shows again.
   */
  fun resetColumnWidths() {
    for (column in columnCache) column.clearWidthSetByUser()
    pinnedColumnsNotToLock = pinnedColumns
  }

  /** The columns of [columnModel] in grid order, whichever table shows them. */
  fun visibleColumns(): List<TableResultViewColumn> = List(columnModel.columnCount) { columnModel.getColumn(it) }

  private fun lockWidth(column: TableResultViewColumn) {
    val width = column.pinnedWidth
    column.setPinnedWidth(width, column.isWidthSetByUser)
    column.width = width
  }

  private fun unlockWidth(column: TableResultViewColumn) {
    val width = column.columnWidth
    if (column.isWidthSetByUser) column.setColumnWidthByUser(width) else column.setColumnWidth(width)
    column.width = width
  }

  override fun dispose() {
    disposed = true
  }

  /** Whether the grid keeps the column order, and asks the view to apply it. Otherwise the view places columns in model order. */
  private fun gridKeepsColumnOrder(): Boolean = !disposed && grid is ColumnOrderRestorer

  /** Asks the grid to arrange the columns, and returns whether it did. */
  private fun applyColumnsDisplayOrder(): Boolean = !disposed && host.applyColumnsDisplayOrder()

  fun createDefaultColumnsFromModel() {
    host.columnsRemoving()

    columnModel.removeAllColumns()

    val newModelIndices = IntArrayList()
    for (modelColumnIdx in 0 until dataModel.columnCount) {
      val notShownEarlier = !columnCache.hasCachedColumn(modelColumnIdx)
      val tableColumn = columnCache.getOrCreateColumn(modelColumnIdx)
      val collapseManager = grid.hierarchicalColumnsCollapseManager
      // Adds the enabled columns itself, not through setColumnEnabled, and reports the new ones in one columnsShown call below.
      val enabled = transposed ||
                    grid.isColumnEnabled(ModelIndex.forColumn(grid, modelColumnIdx)) &&
                    !(collapseManager != null &&
                      collapseManager.isColumnHiddenDueToCollapse(ModelIndex.forColumn(grid, modelColumnIdx)))
      if (enabled) {
        columnModel.addColumn(tableColumn)
        if (notShownEarlier) {
          newModelIndices.add(modelColumnIdx)
        }
      }
    }

    applyColumnsDisplayOrder()
    if (!newModelIndices.isEmpty) host.columnsShown(modelIndexSet(newModelIndices.toIntArray()))
  }

  /** The model indices of the columns of [columnModel], which are grid rows when transposed. */
  private fun modelIndexSet(modelIndices: IntArray): ModelIndexSet<*> =
    if (transposed) ModelIndexSet.forRows(grid, *modelIndices) else ModelIndexSet.forColumns(grid, *modelIndices)

  fun setColumnEnabled(columnIdx: ModelIndex<GridColumn>, enabled: Boolean) {
    if (transposed) {
      dataModel.fireTableDataChanged()
    }
    else {
      setColumnShown(columnIdx, enabled, restoreOrder = true)
    }
  }

  fun setColumnsEnabled(columns: List<ModelIndex<GridColumn>>, enabled: Boolean) {
    if (columns.isEmpty()) return
    if (transposed) {
      dataModel.fireTableDataChanged()
      return
    }
    val deferOrder = enabled && gridKeepsColumnOrder()
    for (column in columns) setColumnShown(column, enabled, restoreOrder = !deferOrder)
    if (deferOrder) applyColumnsDisplayOrder()
  }

  fun setRowEnabled(rowIdx: ModelIndex<GridRow>, enabled: Boolean) {
    if (transposed) {
      setColumnShown(rowIdx, enabled, restoreOrder = true)
    }
    else {
      dataModel.fireTableDataChanged()
    }
  }

  /**
   * Shows or hides the column at [modelColumnIdx]. With [restoreOrder], a newly shown column takes its place in the grid's display order,
   * or in model order when the grid applies none. Without it, the column goes last and the caller orders the columns.
   */
  private fun setColumnShown(modelColumnIdx: ModelIndex<*>, shown: Boolean, restoreOrder: Boolean) {
    val viewColumnIdx = modelColumnIdx.toView(grid)
    if (shown && viewColumnIdx.asInteger() < 0) {
      val firstTimeShown = !columnCache.hasCachedColumn(modelColumnIdx.asInteger())
      addColumnInOrder(modelColumnIdx, restoreOrder)
      if (firstTimeShown) host.columnsShown(modelIndexSet(intArrayOf(modelColumnIdx.asInteger())))
    }
    else if (!shown && viewColumnIdx.asInteger() >= 0) {
      removeColumnAt(viewColumnIdx)
    }
  }

  private fun addColumnInOrder(modelColumnIdx: ModelIndex<*>, restoreOrder: Boolean) {
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

  private fun removeColumnAt(viewColumnIdx: ViewIndex<*>) {
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

  /**
   * Moves each column to the position that [modelIndexAtPosition] gives for its model index. Positions the map lacks stay as they are.
   * Does nothing while transposed.
   */
  fun restoreColumnsOrder(modelIndexAtPosition: Map<Int, ModelIndex<GridColumn>>) {
    if (transposed) return
    for (position in 0 until columnModel.columnCount) {
      val modelIndex = modelIndexAtPosition[position] ?: continue
      // The grid's converter caches the positions until a column is added, removed or moved.
      val currentPosition = modelIndex.toView(grid).asInteger()
      if (currentPosition != -1 && currentPosition != position) columnModel.moveColumn(currentPosition, position)
    }
  }

  /** The cached column of [column] while the view shows it, otherwise null. */
  fun shownColumn(column: ModelIndex<*>): TableResultViewColumn? =
    if (column.toView(grid).asInteger() != -1) columnCache.getOrCreateColumn(column.asInteger()) else null

  /**
   * Takes the data model of a transpose or of the way back. Drops the cached columns of the old model; on the way in, keeps the
   * untransposed widths for [restoreUntransposedColumnWidths].
   */
  fun setDataModel(model: GridTableModel) {
    if (model is TransposedGridTableModel) rememberUntransposedColumnWidths()
    dataModel = model
    columnCache.transposed = transposed
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
      val column = shownColumn(ModelIndex.forColumn(grid, entry.intKey)) ?: continue
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
      val neighborPosition = e.toIndex + if (e.fromIndex < e.toIndex) -1 else 1
      val column = ModelIndex.forColumn(grid, columnModel.getColumn(e.toIndex).modelIndex)
      val neighbor = ModelIndex.forColumn(grid, columnModel.getColumn(neighborPosition).modelIndex)
      host.columnMoved(ColumnMove(column, neighbor, e.fromIndex > e.toIndex))
    }

    override fun columnAdded(e: TableColumnModelEvent) = Unit
    override fun columnRemoved(e: TableColumnModelEvent) = Unit
    override fun columnMarginChanged(e: ChangeEvent) = Unit
    override fun columnSelectionChanged(e: ListSelectionEvent) = Unit
  }
}
