package com.intellij.database.datagrid

import com.intellij.database.datagrid.NestedTablesDataGridModel.NestedTableCellCoordinate
import com.intellij.database.datagrid.nested.NestedTablesAware
import com.intellij.database.datagrid.nested.NestedTablesAware.NonEmptyStack

/**
 * The NestedTableGridPagingModel class is an implementation of the MultiPageModel interface designed to work with
 * a grid model that contains nested tables. It supports pagination and navigation between nested tables.
 *
 * @param <Row>    the type of the row in the grid
 * @param <Column> the type of the column in the grid
</Column></Row> */
class NestedTableGridPagingModel<Row, Column>(
  private val myGridModel: GridModelWithNestedTables,
  private val myTopLevelPagingModel: MultiPageModel<Row, Column>
) : MultiPageModel<Row, Column>, NestedTablesAware<Void?> {
  private val myNestedTablePageModels = NonEmptyStack<MultiPageModel<Row, Column>>(myTopLevelPagingModel)

  private var myCurrentPagingModel: MultiPageModel<Row, Column>

  private val myListeners: MutableList<MultiPageModel.PageModelListener>

  init {
    myCurrentPagingModel = myTopLevelPagingModel
    myListeners = ArrayList<MultiPageModel.PageModelListener>()
  }

  override suspend fun enterNestedTable(coordinate: NestedTableCellCoordinate, nestedTable: NestedTable): Void? {
    navigateIntoNestedTable(nestedTable)
    return null
  }

  override suspend fun exitNestedTable(steps: Int): Void? {
    navigateBackFromNestedTable(steps)
    return null
  }

  /**
   * Navigates into a nested table in the grid paging model.
   *
   * @param newSelectedNestedTable The nested table to navigate into.
   *
   */
  @Deprecated(
    "This method is deprecated and marked for removal. Use the {@link #enterNestedTable(NestedTableCellCoordinate, NestedTable)} method instead.")
  fun navigateIntoNestedTable(newSelectedNestedTable: NestedTable) {
    myCurrentPagingModel = createPageModel(newSelectedNestedTable)
    myNestedTablePageModels.push(myCurrentPagingModel)
  }

  /**
   * Navigates back from a nested table in the grid paging model.
   *
   *
   * If the given `steps` parameter is negative or equals to or greater than
   * the depth of stack of nested tables, an exception is thrown.
   *
   * @param steps The number of steps to navigate back. This parameter
   * must be a non-negative integer less than the current depth
   * of [.myNestedTablePageModels] stack, i.e., it should be within
   * the range [0, size).
   *
   */
  @Deprecated("This method is deprecated and marked for removal. Use the {@link #exitNestedTable(int)}} method instead.")
  fun navigateBackFromNestedTable(steps: Int) {
    myNestedTablePageModels.pop(steps)
    myCurrentPagingModel = myNestedTablePageModels.last()
  }

  fun reset() {
    myCurrentPagingModel = myTopLevelPagingModel
    myNestedTablePageModels.reset(myTopLevelPagingModel)
  }

  private fun createPageModel(newSelectedNestedTable: NestedTable): MultiPageModel<Row, Column> {
    val model = if (newSelectedNestedTable is StaticNestedTable)
      StaticMultiPageModel(myGridModel as GridModel<Row, Column>, myTopLevelPagingModel)
    else
      MultiPageModelImpl(myGridModel as GridModel<Row, Column>, null)
    model.setPageSize(myTopLevelPagingModel.getPageSize())
    for (listener in myListeners) {
      model.addPageModelListener(listener)
    }

    return model
  }

  override fun isFirstPage(): Boolean {
    return myCurrentPagingModel.isFirstPage()
  }

  override fun isLastPage(): Boolean {
    return myCurrentPagingModel.isLastPage()
  }

  override fun setPageSize(pageSize: Int) {
    myCurrentPagingModel.setPageSize(pageSize)
  }

  override fun getPageSize(): Int {
    return myCurrentPagingModel.getPageSize()
  }

  override fun getTotalRowCount(): Long {
    return myCurrentPagingModel.getTotalRowCount()
  }

  override fun isTotalRowCountPrecise(): Boolean {
    return myCurrentPagingModel.isTotalRowCountPrecise()
  }

  override fun isTotalRowCountUpdateable(): Boolean {
    return myCurrentPagingModel.isTotalRowCountUpdateable()
  }

  override fun getPageStart(): Int {
    return myCurrentPagingModel.getPageStart()
  }

  override fun getPageEnd(): Int {
    return myCurrentPagingModel.getPageEnd()
  }

  override fun findRow(rowNumber: Int): ModelIndex<Row?> {
    return myCurrentPagingModel.findRow(rowNumber)
  }

  override fun pageSizeSet(): Boolean {
    return myCurrentPagingModel.pageSizeSet()
  }

  override fun setPageStart(pageStart: Int) {
    myCurrentPagingModel.setPageStart(pageStart)
  }

  override fun setPageEnd(pageEnd: Int) {
    myCurrentPagingModel.setPageEnd(pageEnd)
  }

  override fun setTotalRowCount(totalRowCount: Long, precise: Boolean) {
    myCurrentPagingModel.setTotalRowCount(totalRowCount, precise)
  }

  override fun setTotalRowCountUpdateable(updateable: Boolean) {
    myCurrentPagingModel.setTotalRowCountUpdateable(updateable)
  }

  override fun addPageModelListener(listener: MultiPageModel.PageModelListener) {
    myListeners.add(listener)
    for (model in myNestedTablePageModels) {
      model.addPageModelListener(listener)
    }
  }

  val isStatic: Boolean
    get() = myCurrentPagingModel is StaticMultiPageModel<Row, Column>

  /**
   * The StaticMultiPageModel class is an implementation of the MultiPageModel interface designed to work with
   * ArrayBackedNestedTable that does not support pagination. This class behaves as a SinglePage model but implements
   * MultiPageModel for interface compatibility. Consequently, several methods do not perform any operations in this
   * implementation.
   * It is intended to be used together with [NestedTableGridPagingModel]
   *
   * @param <Row>    the type of the row in the grid
   * @param <Column> the type of the column in the grid
  </Column></Row> */
  class StaticMultiPageModel<Row, Column>(
    private val myGridModel: GridModel<Row, Column>,
    private val myDelegate: GridPagingModel<Row, Column>
  ) : MultiPageModel<Row, Column> {
    override fun isFirstPage(): Boolean {
      return this.isNestedGrid || myDelegate.isFirstPage()
    }

    private val isNestedGrid: Boolean
      get() = myGridModel is HierarchicalColumnsDataGridModel && !myGridModel.isTopLevelGrid

    override fun isLastPage(): Boolean {
      return this.isNestedGrid || myDelegate.isLastPage()
    }

    override fun setPageSize(pageSize: Int) {
      if (!this.isNestedGrid) myDelegate.setPageSize(pageSize)
    }

    override fun getPageSize(): Int {
      return if (this.isNestedGrid) myGridModel.getRowCount() else myDelegate.getPageSize()
    }

    override fun getTotalRowCount(): Long {
      return if (this.isNestedGrid)
        myGridModel.getRowCount()
          .toLong()
      else
        myDelegate.getTotalRowCount()
    }

    override fun isTotalRowCountPrecise(): Boolean {
      return this.isNestedGrid || myDelegate.isTotalRowCountPrecise()
    }

    override fun isTotalRowCountUpdateable(): Boolean {
      return this.isNestedGrid || myDelegate.isTotalRowCountUpdateable()
    }

    override fun getPageStart(): Int {
      return if (this.isNestedGrid) 1 else myDelegate.getPageStart()
    }

    override fun getPageEnd(): Int {
      return if (this.isNestedGrid) myGridModel.getRowCount() else myDelegate.getPageEnd()
    }

    override fun findRow(rowNumber: Int): ModelIndex<Row?> {
      return if (this.isNestedGrid)
        ModelIndex.forRow<Row?>(myGridModel, rowNumber - 1)
      else
        myDelegate.findRow(rowNumber)
    }

    override fun pageSizeSet(): Boolean {
      return this.isNestedGrid || myDelegate.pageSizeSet()
    }

    override fun setPageStart(pageStart: Int) {}

    override fun setPageEnd(pageEnd: Int) {}

    override fun setTotalRowCount(totalRowCount: Long, precise: Boolean) {}

    override fun setTotalRowCountUpdateable(updateable: Boolean) {}

    override fun addPageModelListener(listener: MultiPageModel.PageModelListener) {}
  }
}
