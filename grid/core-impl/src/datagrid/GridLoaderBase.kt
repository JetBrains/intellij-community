package com.intellij.database.datagrid

import com.intellij.database.run.ui.grid.GridModelUpdater
import kotlin.math.max
import kotlin.math.min

abstract class GridLoaderBase(protected val hookUp: GridDataHookUpBase<GridRow, GridColumn>,
                              protected val pageModel: MultiPageModel<GridRow, GridColumn>,
                              protected val modelUpdater: GridModelUpdater) : GridLoader {
  override fun reloadCurrentPage(source: GridRequestSource) = load(source, max(0, pageModel.pageStart - 1))
  override fun loadNextPage(source: GridRequestSource) = load(source, pageModel.pageEnd)
  override fun loadPreviousPage(source: GridRequestSource) = load(source, max(0, pageModel.pageStart - pageModel.pageSize - 1))
  override fun loadFirstPage(source: GridRequestSource) = load(source, 0)
  override fun updateTotalRowCount(source: GridRequestSource) = hookUp.notifyRequestFinished(source, false)
  override fun applyFilterAndSorting(source: GridRequestSource) = hookUp.notifyRequestFinished(source, false)
  override fun updateIsTotalRowCountUpdateable() = Unit

  override fun loadLastPage(source: GridRequestSource) {
    val pageSize: Int = pageModel.pageSize
    load(source, -(if (pageSize > 0) pageSize else 100))
  }

  protected fun getPositiveOffset(offset: Int): Long = if (offset < 0) pageModel.totalRowCount + offset else offset.toLong()

  protected fun getCount(positiveOffset: Long): Long {
    return min(pageModel.totalRowCount,
               if (GridUtilCore.isPageSizeUnlimited(pageModel.pageSize)) pageModel.totalRowCount - positiveOffset
               else pageModel.pageSize.toLong())
  }

  protected fun afterLastRowAdded(rowsLoaded: Int, source: GridRequestSource) {
    if (rowsLoaded >= 0 && rowsLoaded < hookUp.dataModel.rowCount) {
      val rowsToRemove: Int = hookUp.dataModel.rowCount - rowsLoaded
      modelUpdater.removeRows(hookUp.dataModel.rowCount - rowsToRemove, rowsToRemove)
    }
    modelUpdater.afterLastRowAdded()
    source.requestComplete(true)
  }

  protected fun addRows(rows: List<GridRow>, rowsLoaded: Int): Int {
    if (rows.isEmpty()) return 0
    if (rowsLoaded == 0) pageModel.pageStart = rows[0].rowNum
    pageModel.pageEnd = rows[rows.size - 1].rowNum
    modelUpdater.setRows(rowsLoaded, rows, GridRequestSource(null))
    return rows.size
  }

  protected fun loadingStarted(offset: Int) {
    (hookUp.dataModel as? GridListModelBase)?.isUpdatingNow = true
    pageModel.pageStart = offset + 1
    pageModel.pageEnd = offset
  }
}