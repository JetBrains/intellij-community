package com.intellij.database.run.ui.columns

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.DataGridListener
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridHelper
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.run.actions.ColumnPinCommands
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.TableResultPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.concurrency.CancellablePromise
import javax.swing.Icon

/** Owns the column list state, grid commands, and presentation loading. The popup owns its widgets. */
internal class ColumnsListController(
  private val grid: DataGrid,
  private val disposable: Disposable,
  private val render: (preserveSelection: Boolean) -> Unit,
) {
  val commands = ColumnPinCommands(grid)

  var model = ColumnsListModel(emptyList())
    private set

  private var applying = false
  private var disposed = false
  private var columnSnapshot: Map<ModelIndex<GridColumn>, GridColumn?>? = null
  private var presentationTask: CancellablePromise<*>? = null

  init {
    Disposer.register(disposable) { disposed = true }
  }

  fun start() {
    grid.addDataGridListener(object : DataGridListener {
      override fun onContentChanged(dataGrid: DataGrid, place: GridRequestSource.RequestPlace?) {
        if (!applying) refresh()
      }
    }, disposable)
    refresh()
  }

  fun search(text: String) {
    model = model.withFilter(ColumnsListFilter(text.trim()))
    render(true)
  }

  fun hasCurrentColumns(): Boolean {
    val snapshot = columnSnapshot ?: return false
    val dataModel = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
    val indices = dataModel.columnIndices
    return indices.size() == snapshot.size && indices.asIterable().all { index ->
      snapshot.containsKey(index) && snapshot[index] === dataModel.getColumn(index)
    }
  }

  /** Rejects a command when its columns have been replaced. */
  fun ensureCurrentColumns(): Boolean {
    if (disposed) return false
    if (hasCurrentColumns()) return true
    refresh()
    return false
  }

  fun applyVisibility(columns: List<ModelIndex<GridColumn>>, visible: Boolean) {
    if (!ensureCurrentColumns()) return
    val targets = columns.filter { grid.isColumnEnabled(it) != visible }
    if (targets.isEmpty()) return
    apply { targets.forEach { grid.setColumnEnabled(it, visible) } }
  }

  fun canMove(column: ModelIndex<GridColumn>, target: ModelIndex<GridColumn>): Boolean =
    (grid as? TableResultPanel)?.canMoveColumnInDisplayOrder(column, target) == true

  fun move(column: ModelIndex<GridColumn>, target: ModelIndex<GridColumn>, before: Boolean) {
    if (!ensureCurrentColumns() || !canMove(column, target)) return
    apply { (grid as TableResultPanel).moveColumnInDisplayOrder(column, target, before) }
  }

  fun togglePin(column: ModelIndex<GridColumn>) {
    if (!ensureCurrentColumns()) return
    if (model.items.none { it.modelIndex == column && it.canTogglePin }) return
    apply { commands.togglePin(column) }
  }

  fun pin(columns: ModelIndexSet<GridColumn>) {
    if (ensureCurrentColumns()) apply { commands.pin(columns) }
  }

  fun unpin(columns: ModelIndexSet<GridColumn>) {
    if (ensureCurrentColumns()) apply { commands.unpin(columns) }
  }

  fun pinUpToHere(column: ModelIndex<GridColumn>) {
    if (ensureCurrentColumns()) apply { commands.pinUpToHere(column) }
  }

  fun unpinAll() {
    if (ensureCurrentColumns()) apply { commands.unpinAll() }
  }

  fun isOriginalOrder(): Boolean = (grid as? TableResultPanel)?.isColumnsOrderModified != true

  fun restoreOriginalOrder() {
    if (!ensureCurrentColumns()) return
    val panel = grid as? TableResultPanel ?: return
    apply { panel.restoreNaturalColumnsOrder() }
  }

  fun selectColumns(columns: List<ModelIndex<GridColumn>>) {
    if (!ensureCurrentColumns()) return
    val visible = columns.filter { grid.isColumnEnabled(it) }
    grid.selectionModel.setColumnSelection(ModelIndexSet.forColumns(grid, visible), true)
  }

  private fun apply(operation: () -> Unit) {
    applying = true
    try {
      operation()
    }
    finally {
      applying = false
    }
    refresh()
  }

  private fun refresh() {
    if (disposed) return
    val columnsChanged = !hasCurrentColumns()
    if (columnsChanged) {
      presentationTask?.cancel()
      val dataModel = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
      columnSnapshot = dataModel.columnIndices.asIterable().associateWith { dataModel.getColumn(it) }
    }
    val previous = if (columnsChanged) emptyMap() else model.items.associateBy { it.modelIndex }
    val items = buildColumnsListItems(grid) { column ->
      val item = previous[ModelIndex.forColumn(grid, column.columnNumber)]
      if (item == null) column.typeName to null else item.typeText to item.icon
    }
    updateItems(items, preserveSelection = !columnsChanged)
    if (columnsChanged) loadPresentations(columnSnapshot!!)
  }

  private fun updateItems(items: List<ColumnsListItem>, preserveSelection: Boolean = true) {
    if (preserveSelection && items == model.items) return
    model = ColumnsListModel(items, model.filter)
    render(preserveSelection)
  }

  private fun loadPresentations(snapshot: Map<ModelIndex<GridColumn>, GridColumn?>) {
    if (snapshot.isEmpty()) return
    val helper = GridHelper.get(grid)
    presentationTask = ReadAction.nonBlocking<Map<ModelIndex<GridColumn>, Pair<String?, Icon?>?>> {
      snapshot.mapValues { (_, column) ->
        ProgressManager.checkCanceled()
        column?.let { helper.getColumnTypeText(grid, it) to helper.getColumnIcon(grid, it, true) }
      }
    }
      .expireWith(disposable)
      .expireWith(grid)
      .finishOnUiThread(ModalityState.defaultModalityState()) { presentations ->
        if (columnSnapshot !== snapshot || !ensureCurrentColumns()) return@finishOnUiThread
        updateItems(model.items.map { item ->
          val presentation = presentations[item.modelIndex]
          if (presentation == null) item else item.copy(typeText = presentation.first, icon = presentation.second)
        })
      }
      .submit(AppExecutorUtil.getAppExecutorService())
  }
}
