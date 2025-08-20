package com.intellij.database.run.ui.grid

import com.intellij.database.datagrid.*
import com.intellij.database.extractors.DatabaseObjectFormatterConfig.DatabaseDisplayObjectFormatterConfig
import com.intellij.database.run.ui.DataAccessType
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.checkCanceled
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

internal typealias SearchInfo = List<TreeSet<Int>?>

@OptIn(FlowPreview::class)
internal class DataGridSearchSessionWorker(
  cs: CoroutineScope,
  private val grid: DataGrid,
  private val findManager: FindManager,
  private val findModel: FindModel,
) {

  @Volatile
  private var searchInfo: SearchInfo? = null

  // nothing actually guarantees that the info won't get immediately invalidated after call
  // unless you externally synchronize via being on EDT or somehow else
  fun hasInfo(): Boolean = searchInfo != null

  private val requests: MutableSharedFlow<Boolean> =
    MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val onUpdated: MutableSharedFlow<Boolean> =
    MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)


  init {
    cs.launch {
      requests
        .debounce(100.milliseconds)
        .collectLatest { doNotSelectOccurence ->
          searchInfo = search()
          onUpdated.tryEmit(doNotSelectOccurence)
        }
    }
  }

  // for poor java callers
  fun subscribeOnUpdateEDT(action: (x: Boolean) -> Unit, cs: CoroutineScope) {
    onUpdated.onEach { withContext(Dispatchers.EDT) { action(it) } }.launchIn(cs)
  }

  fun isMatchedCell(rowIdx: ModelIndex<GridRow>, columnIdx: ModelIndex<GridColumn>): Boolean {
    val searchInfoNow = searchInfo ?: return false
    return searchInfoNow
      .getOrNull(rowIdx.asInteger())
      ?.contains(columnIdx.asInteger()) == true
  }

  private fun calcCellMatch(row: GridRow, column: GridColumn, config: DatabaseDisplayObjectFormatterConfig): Boolean {
    val cellText = GridUtil.getText(grid, row, column, config)
    return findManager.findString(cellText, 0, findModel).isStringFound
  }

  private suspend fun search(): SearchInfo {
    val model = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
    val rowIdxs = model.rowIndices.asList().sortedBy { it.asInteger() }
    val columnIdxs = model.columnIndices.asList().sortedBy { it.asInteger() }
    val configs = columnIdxs.map { grid.getFormatterConfig(it) ?: DatabaseDisplayObjectFormatterConfig()}.toList()
    val result = ArrayList<TreeSet<Int>?>()
    result.addAll((0 until rowIdxs.size).map { null })

    rowIdxs.forEachIndexed { rowNum, rowIdx ->
      val row = model.getRow(rowIdx)
      columnIdxs.forEachIndexed { colNum, columnIdx ->
        val column = model.getColumn(columnIdx)
        if (rowNum != rowIdx.asInteger() || colNum != columnIdx.asInteger()) {
          error("unexpected model indices - rowNum: $rowNum, rowIdx.asInteger: ${rowIdx.asInteger()}, colNum: $colNum, columnIdx.asInteger: ${columnIdx.asInteger()}")
        }
        val matched = row?.let { column?.let { calcCellMatch(row, column, configs[colNum]) } } ?: false
        if (matched) {
          val collection = result.getOrNull(rowNum)
          if (collection == null) {
            result[rowNum] = sortedSetOf(columnIdx.asInteger())
          } else {
            collection.add(columnIdx.asInteger())
          }
        }
      }
      checkCanceled()
    }
    return result
  }

  @RequiresEdt
  fun submitStartSearch() {
    searchInfo = null
    requests.tryEmit(true)
  }

  @RequiresEdt
  fun submitStartSearchWithoutSelection() {
    searchInfo = null
    requests.tryEmit(false)
  }

  fun getOccurence(
    includeStart: Boolean,
    direction: SearchDirection
  ): Pair<ModelIndex<GridRow>, ModelIndex<GridColumn>>? {
    val visibilityModel = VisibilityModel(grid.visibleRows, grid.visibleColumns)
    val leadSelectionRow = grid.selectionModel.leadSelectionRow
    val leadSelectionColumn = grid.selectionModel.leadSelectionColumn
    val selectionModel =
      if (leadSelectionRow.isValid(grid) && leadSelectionColumn.isValid(grid))
      SelectionModel(leadSelectionRow, leadSelectionColumn)
      else null
    val searchInfoNow = searchInfo ?: return null
    return getOccurence(searchInfoNow, visibilityModel, selectionModel, includeStart, direction)
  }

  fun getOccurence(
    searchInfo: SearchInfo,
    visibilityModel: VisibilityModel,
    selectionModel: SelectionModel?,
    includeStart: Boolean,
    direction: SearchDirection
  ): Pair<ModelIndex<GridRow>, ModelIndex<GridColumn>>? {
    if (visibilityModel.visibleRows.size() == 0 || visibilityModel.visibleColumns.size() == 0) {
      return null
    }
    val mySelectionModel = selectionModel ?: SelectionModel(visibilityModel.visibleRows.first(), visibilityModel.visibleColumns.first())
    if (includeStart && isMatchedCell(mySelectionModel.rowIdx, mySelectionModel.columnIdx)) {
      return Pair(mySelectionModel.rowIdx, mySelectionModel.columnIdx)
    }
    val visibleColumns = visibilityModel.visibleColumns.asIterable().map { it.asInteger() }.toSortedSet()
    val visibleRows = visibilityModel.visibleRows.asIterable().map { it.asInteger() }.toSortedSet()
    val currentRow = mySelectionModel.rowIdx.asInteger()

    // check current row
    val currentRowMatch = firstAfterCell(mySelectionModel.columnIdx.asInteger(), searchInfo[currentRow], visibleColumns, direction)
    if (currentRowMatch != null) {
      return Pair(ModelIndex.forRow(grid, currentRow), ModelIndex.forColumn(grid, currentRowMatch))
    }

    // check other rows
    var nextRow = next(currentRow, searchInfo.size, direction)
    while (nextRow != currentRow) {
      if (!visibleRows.contains(nextRow)) {
        nextRow = next(nextRow, searchInfo.size, direction)
        continue
      }

      val firstMatched = first(searchInfo[nextRow], visibleColumns, direction)
      if (firstMatched != null) {
        return Pair(ModelIndex.forRow(grid, nextRow), ModelIndex.forColumn(grid, firstMatched))
      }
      nextRow = next(nextRow, searchInfo.size, direction)
    }

    return null
  }

  private fun next(i: Int, max: Int, direction: SearchDirection): Int {
    return when (direction) {
      SearchDirection.FORWARD -> if (i + 1 >= max) 0 else i + 1
      SearchDirection.BACKWARD -> if (i - 1 < 0) max - 1 else i - 1
    }
  }

  private fun first(row: SortedSet<Int>?, visibleColumns: Set<Int>, direction: SearchDirection): Int? {
    return when (direction) {
      SearchDirection.FORWARD -> row?.find { visibleColumns.contains(it) }
      SearchDirection.BACKWARD -> row?.findLast { visibleColumns.contains(it) }
    }
  }

  private fun firstAfterCell(i: Int, row: SortedSet<Int>?, visibleColumns: Set<Int>, direction: SearchDirection): Int? {
    return when (direction) {
      SearchDirection.FORWARD -> row?.find { i < it && visibleColumns.contains(it) }
      SearchDirection.BACKWARD -> row?.findLast { it < i && visibleColumns.contains(it) }
    }
  }

  enum class SearchDirection {
    FORWARD, BACKWARD
  }
  data class VisibilityModel(val visibleRows: ModelIndexSet<GridRow>, val visibleColumns: ModelIndexSet<GridColumn>)
  data class SelectionModel(val rowIdx: ModelIndex<GridRow>, val columnIdx: ModelIndex<GridColumn>)
}