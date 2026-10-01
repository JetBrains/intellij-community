package com.intellij.database.run.ui.columns

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.DataGridListener
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridHelper
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.run.actions.ColumnPinCommands
import com.intellij.database.run.ui.ColumnOrderRestorer
import com.intellij.database.run.ui.DataAccessType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns the column list state, grid commands, and presentation loading. The popup owns its widgets. */
internal class ColumnsListController(
  private val grid: DataGrid,
  private val disposable: Disposable,
  private val scope: CoroutineScope,
  private val render: (preserveSelection: Boolean) -> Unit,
) {
  val commands = ColumnPinCommands(grid)

  var model = ColumnsListModel(emptyList())
    private set

  private val columnOrder = grid as? ColumnOrderRestorer
  private var applying = false
  private var disposed = false
  private var columnSnapshot: Map<ModelIndex<GridColumn>, GridColumn?>? = null
  private val presentationRequests = MutableStateFlow<PresentationRequest?>(null)

  init {
    Disposer.register(disposable) { disposed = true }
  }

  fun start() {
    scope.launch {
      presentationRequests.collectLatest { request ->
        if (request != null) loadPresentations(request)
      }
    }
    grid.addDataGridListener(object : DataGridListener {
      override fun onContentChanged(dataGrid: DataGrid, place: GridRequestSource.RequestPlace?) {
        if (!applying) refresh()
      }

      override fun onColumnOrderChanged(dataGrid: DataGrid) {
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
    apply { grid.setColumnsEnabled(targets, visible) }
  }

  fun canMove(column: ModelIndex<GridColumn>, target: ModelIndex<GridColumn>): Boolean =
    columnOrder?.canMoveColumnInDisplayOrder(column, target) == true

  fun move(column: ModelIndex<GridColumn>, target: ModelIndex<GridColumn>, before: Boolean) {
    if (!ensureCurrentColumns()) return
    val order = columnOrder ?: return
    if (!order.canMoveColumnInDisplayOrder(column, target)) return
    apply { order.moveColumnInDisplayOrder(column, target, before) }
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

  fun isOriginalOrder(): Boolean = columnOrder?.isColumnsOrderModified != true

  fun restoreOriginalOrder() {
    if (!ensureCurrentColumns()) return
    val order = columnOrder ?: return
    apply { order.restoreNaturalColumnsOrder() }
  }

  /** Selects [columns] without moving the grid. */
  fun selectColumns(columns: List<ModelIndex<GridColumn>>) {
    if (!ensureCurrentColumns()) return
    val visible = columns.filter { grid.isColumnEnabled(it) }
    grid.autoscrollLocker.runWithLock {
      grid.selectionModel.setColumnSelection(ModelIndexSet.forColumns(grid, visible), true)
    }
  }

  /** Shows and selects [columns], then scrolls them into view. */
  fun revealColumns(columns: List<ModelIndex<GridColumn>>): Boolean {
    if (!ensureCurrentColumns()) return false
    grid.autoscrollLocker.runWithLock {
      applyVisibility(columns, true)
      selectColumns(columns)
    }
    grid.resultView.scrollColumnsIntoView(columns)
    return true
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
      val dataModel = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
      columnSnapshot = dataModel.columnIndices.asIterable().associateWith { dataModel.getColumn(it) }
    }
    val previous = if (columnsChanged) emptyMap() else model.items.associateBy { it.modelIndex }
    val items = buildColumnsListItems(grid) { column ->
      val item = previous[ModelIndex.forColumn(grid, column.columnNumber)]
      if (item == null) column.typeName to null else item.typeText to item.icon
    }
    updateItems(items, preserveSelection = !columnsChanged)
    if (columnsChanged) {
      presentationRequests.value = PresentationRequest(columnSnapshot!!, ModalityState.defaultModalityState())
    }
  }

  private fun updateItems(items: List<ColumnsListItem>, preserveSelection: Boolean = true) {
    if (preserveSelection && items == model.items) return
    model = ColumnsListModel(items, model.filter)
    render(preserveSelection)
  }

  private suspend fun loadPresentations(request: PresentationRequest) = withContext(request.modality.asContextElement()) {
    val snapshot = request.columns
    val helper = GridHelper.get(grid)
    val presentations = readAction {
      snapshot.mapValues { (_, column) ->
        ProgressManager.checkCanceled()
        column?.let { helper.getColumnTypeText(grid, it) to helper.getColumnIcon(grid, it, true) }
      }
    }
    withContext(Dispatchers.EDT) update@ {
      if (columnSnapshot !== snapshot || !ensureCurrentColumns()) return@update
      updateItems(model.items.map { item ->
        val presentation = presentations[item.modelIndex]
        if (presentation == null) item else item.copy(typeText = presentation.first, icon = presentation.second)
      })
    }
  }

  /** Keeps replacement columns distinct even when their values compare equal. */
  private class PresentationRequest(
    val columns: Map<ModelIndex<GridColumn>, GridColumn?>,
    val modality: ModalityState,
  )
}
