package com.intellij.database.run.ui

import com.intellij.database.DataGridBundle
import com.intellij.database.connection.throwable.info.ErrorInfo
import com.intellij.database.connection.throwable.info.SimpleErrorInfo
import com.intellij.database.datagrid.*
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn
import com.intellij.database.run.ui.table.FilterStateControllerForNestedTables
import com.intellij.database.run.ui.table.LocalFilterState
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.breadcrumbs.Breadcrumbs
import com.intellij.ui.components.breadcrumbs.Crumb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import java.util.function.BiConsumer
import kotlin.math.max

@Service(Service.Level.PROJECT)
private class CoroutineScopeService(val coroutineScope: CoroutineScope)

open class HierarchicalTableResultPanel(
  project: Project,
  dataHookUp: GridDataHookUp<GridRow, GridColumn>,
  popupActions: ActionGroup,
  gutterPopupActions: ActionGroup?,
  columnHeaderActions: ActionGroup,
  rowHeaderActions: ActionGroup,
  useConsoleFonts: Boolean,
  configurator: BiConsumer<DataGrid, DataGridAppearance>,
) : TableResultPanel(project, dataHookUp, popupActions, gutterPopupActions, columnHeaderActions, rowHeaderActions, useConsoleFonts,
                     configurator), DataGridWithNestedTables {
  private val myNestedTablesBreadcrumbs: Breadcrumbs?
  private var myElementsOfBreadcrumbs: MutableList<Crumb>? = null
  private val myNestedTablesNavigationErrorPanel = NestedTablesNavigationErrorPanel()
  private val myLocalFilterStateManager: FilterStateControllerForNestedTables
  private val myHierarchicalColumnsCollapseManager = HierarchicalColumnsCollapseManager(this, columnAttributes)

  constructor(
    project: Project,
    dataHookUp: GridDataHookUp<GridRow, GridColumn>,
    popupActions: ActionGroup,
    configurator: BiConsumer<DataGrid, DataGridAppearance>,
  ) : this(project, dataHookUp, popupActions, null, GridUtil.getGridColumnHeaderPopupActions(), ActionGroup.EMPTY_GROUP, false,
           configurator)

  init {
    val settings = GridUtil.getSettings(this)
    val isEnableLocalFilterByDefault = settings == null || settings.isEnableLocalFilterByDefault()
    myLocalFilterStateManager = FilterStateControllerForNestedTables(this)
    myLocalFilterStateManager.activeFilterState
      .isEnabled = myLocalFilterStateManager.activeFilterState.isEnabled && isEnableLocalFilterByDefault

    if (isNestedTablesSupportEnabled(dataModel)) {
      myNestedTablesBreadcrumbs = Breadcrumbs()
      panel.secondBottomComponent = myNestedTablesBreadcrumbs
      myNestedTablesBreadcrumbs.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          onBreadCrumbClick(e.getX(), e.getY())
        }
      })
      myElementsOfBreadcrumbs = ArrayList<Crumb>().apply {
        add(Crumb.Impl(AllIcons.Nodes.DataTables, " ", null))
      }
      myNestedTablesBreadcrumbs.setCrumbs(myElementsOfBreadcrumbs!!)
    }
    else {
      myNestedTablesBreadcrumbs = null
    }
  }

  override fun rowsRemoved(rows: ModelIndexSet<GridRow?>?) {
    recreateUIElementsRelatedToNestedTableIfNeeded() // dangerous
    super.rowsRemoved(rows)
  }

  override fun cellsUpdated(rows: ModelIndexSet<GridRow?>?, columns: ModelIndexSet<GridColumn?>, place: GridRequestSource.RequestPlace?) {
    recreateUIElementsRelatedToNestedTableIfNeeded()
    super.cellsUpdated(rows, columns, place)
  }

  /**
   * Checks if the UI elements related to the nested table need to be recreated.
   * This may be necessary in the event of data reloading in the grid with nested tables,
   * causing changes in the structure of the nested tables and invalidates the current UI. .
   */
  private fun recreateUIElementsRelatedToNestedTableIfNeeded() {
    if (myElementsOfBreadcrumbs == null) return
    if (!isNestedTablesSupportEnabled(dataModel)) return
    val modelWithNestedTables = dataModel as GridModelWithNestedTables
    val pathToSelectedNestedTable: MutableList<NestedTablesDataGridModel.NestedTableCellCoordinate> = modelWithNestedTables.pathToSelectedNestedTable
    if (pathToSelectedNestedTable.size == myElementsOfBreadcrumbs!!.size - 1) return
    updateBreadCrumbs(pathToSelectedNestedTable)

    project.service<CoroutineScopeService>().coroutineScope.launch {
      updateNestedPageModels(pathToSelectedNestedTable)
      recreateColumns()
    }
  }

  private fun recreateColumns() {
    columnsAdded(dataModel.getColumnIndices())
  }

  private fun updateBreadCrumbs(pathToSelectedNestedTable: MutableList<NestedTablesDataGridModel.NestedTableCellCoordinate>) {
    myElementsOfBreadcrumbs!!.subList(1, myElementsOfBreadcrumbs!!.size).clear()
    for (cellCoordinate in pathToSelectedNestedTable) {
      val column = cellCoordinate.column
      val rowNum = cellCoordinate.rowNum

      @Suppress("HardCodedStringLiteral")
      val breadCrumbText = String.format("%s[%d]", column.getName(), rowNum)
      myElementsOfBreadcrumbs!!.add(Crumb.Impl(null, breadCrumbText, null))
    }
    myNestedTablesBreadcrumbs!!.setCrumbs(myElementsOfBreadcrumbs!!)
  }

  private suspend fun updateNestedPageModels(pathToSelectedNestedTable: MutableList<NestedTablesDataGridModel.NestedTableCellCoordinate>) {
    val nestedPaging = dataHookup.getPageModel() as? NestedTableGridPagingModel<GridRow?, GridColumn?> ?: return
    nestedPaging.reset()
    for (cellCoordinate in pathToSelectedNestedTable) {
      val cellValue = cellCoordinate.column.getValue(cellCoordinate.row)
      if (cellValue !is NestedTable) break
      nestedPaging.enterNestedTable(cellCoordinate, cellValue)
    }
  }

  private fun appendNestedTableBreadcrumb(colName: @NlsSafe String?, rowNum: Int) {
    @Suppress("HardCodedStringLiteral")
    val breadCrumbText = String.format("%s[%d]", colName, rowNum)
    myElementsOfBreadcrumbs!!.add(Crumb.Impl(null, breadCrumbText, null))
    myNestedTablesBreadcrumbs!!.setCrumbs(myElementsOfBreadcrumbs!!)
  }

  override fun onCellClick(rowIdx: ModelIndex<GridRow?>, colIdx: ModelIndex<GridColumn?>): Boolean {
    project.service<CoroutineScopeService>().coroutineScope.launch(Dispatchers.EDT) {
      if (tryUpdateGridToShowNestedTable(rowIdx, colIdx)) {
        if (dataModel.getRowCount() != 0) {
          resultView.resetScroll()
        }
      }
    }
    return true
  }

  private suspend fun tryUpdateGridToShowNestedTable(rowIdx: ModelIndex<GridRow?>, colIdx: ModelIndex<GridColumn?>): Boolean {
    if (myNestedTablesBreadcrumbs == null) return false
    if (!isNestedTableSupportEnabled()) return false

    val row = dataModel.getRow(rowIdx)
    val column = dataModel.getColumn(colIdx)
    if (column == null || row == null) return false
    val cellValue = dataModel.getValueAt(rowIdx, colIdx)
    if (cellValue !is NestedTable) return false

    if (!isReady) {
      myNestedTablesNavigationErrorPanel.show(SimpleErrorInfo.create(DataGridBundle.message("grid.load.nested.table.error.loading")))
      return false
    }
    else {
      myNestedTablesNavigationErrorPanel.hideIfShown()
    }

    if (!tryLoadNestedTable(cellValue, row, column)) return false

    recreateColumns()
    appendNestedTableBreadcrumb(column.getName(), row.getRowNum())

    return true
  }

  private suspend fun tryLoadNestedTable(newSelectedNestedTable: NestedTable, row: GridRow, column: GridColumn): Boolean {
    val modelWithNestedTables = dataModel as GridModelWithNestedTables
    val coordinate = NestedTablesDataGridModel.NestedTableCellCoordinate(row, column)
    myLocalFilterStateManager.enterNestedTable(coordinate, newSelectedNestedTable)
    (dataHookup.getSortingModel() as? NestedTablesSortingModel<*>)?.apply {
      enterNestedTable(coordinate, newSelectedNestedTable)
    }

    val nestedTablesAwareGridLoader = dataHookup.getLoader() as? NestedTablesGridLoader
    if (nestedTablesAwareGridLoader != null) {
      if (nestedTablesAwareGridLoader.isLoadAllowed()) {
        myNestedTablesNavigationErrorPanel.show(SimpleErrorInfo.create(DataGridBundle.message("grid.load.nested.table.error")))
        return false
      }
      else {
        myNestedTablesNavigationErrorPanel.hideIfShown()
      }

      modelWithNestedTables.enterNestedTable(coordinate, newSelectedNestedTable)

      (dataHookup.getPageModel() as? NestedTableGridPagingModel<GridRow?, GridColumn?>)?.apply {
        enterNestedTable(coordinate, newSelectedNestedTable)
      }

      if (newSelectedNestedTable !is StaticNestedTable) {
        nestedTablesAwareGridLoader.enterNestedTable(coordinate, newSelectedNestedTable)
        dataHookup.getLoader().loadFirstPage(GridRequestSource(DataGridRequestPlace(this)))
      }
    }
    else {
      modelWithNestedTables.enterNestedTable(coordinate, newSelectedNestedTable)
      (dataHookup.getPageModel() as? NestedTableGridPagingModel<GridRow?, GridColumn?>)?.apply {
        enterNestedTable(coordinate, newSelectedNestedTable)
      }
    }

    return true
  }

  override fun onBreadCrumbClick(x: Int, y: Int) {
    if (myNestedTablesBreadcrumbs == null) return
    if (!isNestedTableSupportEnabled()) return

    val clicked = myNestedTablesBreadcrumbs.getCrumbAt(x, y)
    if (clicked == null) return

    val idx = myElementsOfBreadcrumbs!!.indexOf(clicked)
    val depth = myElementsOfBreadcrumbs!!.size
    // the current table clicked. nothing to do
    if (idx == depth - 1) return

    if (dataModel.isUpdatingNow()) {
      val text = clicked.text
      myNestedTablesNavigationErrorPanel.show(
        SimpleErrorInfo.create(
          DataGridBundle.message("grid.load.prev.nested.table.error.loading", text.ifBlank { "top level" })))
      return
    }
    else {
      myNestedTablesNavigationErrorPanel.hideIfShown()
    }

    val steps = depth - (idx + 1)

    project.service<CoroutineScopeService>().coroutineScope.launch(Dispatchers.EDT) {
      updateGridToShowPreviousNestedTable(steps)
    }
  }

  private suspend fun updateGridToShowPreviousNestedTable(steps: Int) {
    val modelWithNestedTables = dataModel as GridModelWithNestedTables
    val coordinateToRestoreScroll = modelWithNestedTables.exitNestedTable(steps)
    (dataHookup.getPageModel() as? NestedTableGridPagingModel<GridRow?, GridColumn?>)?.apply {
      exitNestedTable(steps)
    }

    (dataHookup.getLoader() as? NestedTablesGridLoader)?.apply {
      exitNestedTable(steps)
    }
    myLocalFilterStateManager.exitNestedTable(steps)
    (dataHookup.getSortingModel() as? NestedTablesSortingModel<*>)?.apply {
      exitNestedTable(steps)
    }
    recreateColumns()
    removeTail(myElementsOfBreadcrumbs!!, steps)
    myNestedTablesBreadcrumbs!!.setCrumbs(myElementsOfBreadcrumbs!!)
    if (coordinateToRestoreScroll == null) return
    showCell(coordinateToRestoreScroll.rowIdx, coordinateToRestoreScroll.getColumnIdx(this))
  }

  override fun isNestedTableSupportEnabled(): Boolean {
    return (dataModel as? GridModelWithNestedTables)?.isNestedTablesSupportEnabled == true
  }

  override fun isNestedTableStatic(): Boolean {
    return isNestedTableSupportEnabled &&
           (dataModel as GridModelWithNestedTables).selectedNestedTable is StaticNestedTable
  }

  override fun isTopLevelGrid(): Boolean {
    val model = dataModel
    if (model !is GridModelWithNestedTables) return true
    return model.isTopLevelGrid
  }

  override fun afterLastRowAdded() {
    super.afterLastRowAdded()
    myNestedTablesNavigationErrorPanel.hideIfShown()
  }

  override fun getLocalFilterState(): LocalFilterState {
    return myLocalFilterStateManager.activeFilterState
  }

  override fun setColumnEnabled(columnIdx: ModelIndex<GridColumn?>, state: Boolean) {
    val column = getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(columnIdx)
    if (column == null || isColumnEnabled(column) == state) return

    if (column !is HierarchicalGridColumn) {
      updateColumnEnableState(column, state)
      return
    }

    if (state) {
      updateEnableStateForColumns(getDisabledAncestorColumns(column), true)
      updateColumnEnableState(column, true)
      return
    }

    val ancestor = column.parent
    if (column.isLeftMostChildOfDirectAncestor && ancestor != null) {
      updateEnableStateForColumns(ancestor.leaves, false)
    }
    else {
      updateColumnEnableState(column, false)
    }

    resultView.onColumnHierarchyChanged()
  }

  override fun getHierarchicalColumnsCollapseManager(): HierarchicalColumnsCollapseManager? {
    return myHierarchicalColumnsCollapseManager
  }

  private fun getDisabledAncestorColumns(gridColumn: HierarchicalGridColumn): MutableList<out GridColumn> {
    val columnsBranchToUpdate = LinkedList<HierarchicalGridColumn>()
    var ancestor = gridColumn.parent
    while (ancestor != null) {
      val leftMostChild: HierarchicalGridColumn? = ancestor.leaves[0]
      if (false == columnAttributes.isEnabled(leftMostChild)) {
        columnsBranchToUpdate.addFirst(leftMostChild!!)
        ancestor = ancestor.parent
        continue
      }
      break
    }

    return columnsBranchToUpdate
  }

  private fun updateEnableStateForColumns(columns: MutableList<out GridColumn>, state: Boolean) {
    for (c in columns) {
      updateColumnEnableState(c, state)
    }
  }

  private fun updateColumnEnableState(c: GridColumn, state: Boolean) {
    columnAttributes.setEnabled(c, state)

    if (myHierarchicalColumnsCollapseManager.isColumnHiddenDueToCollapse(c)) return

    val selection = selectionModel.store()
    val colIdx = ModelIndex.forColumn<GridColumn?>(this, c.getColumnNumber())
    storeOrRestoreSelection(colIdx, state, selection)
    resultView.setColumnEnabled(colIdx, state)
    fireContentChanged(null) // update structure view
    runWithIgnoreSelectionChanges(Runnable {
      selectionModel.restore(selection)
    })
  }

  internal inner class NestedTablesNavigationErrorPanel {
    private var hasShown = false

    fun show(errorInfo: ErrorInfo) {
      showError(errorInfo, null)
      hasShown = true
    }

    fun hideIfShown() {
      if (hasShown) {
        hideErrorPanel()
        hasShown = false
      }
    }
  }

  companion object {
    private fun removeTail(list: MutableList<*>, k: Int) {
      list.subList(max(0, list.size - k), list.size).clear()
    }

    fun isNestedTablesSupportEnabled(model: GridModel<GridRow?, GridColumn?>?): Boolean {
      return model is GridModelWithNestedTables && (model as GridModelWithNestedTables).isNestedTablesSupportEnabled
    }
  }
}