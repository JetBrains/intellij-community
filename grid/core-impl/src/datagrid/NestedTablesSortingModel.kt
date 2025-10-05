package com.intellij.database.datagrid

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.database.datagrid.NestedTablesDataGridModel.NestedTableCellCoordinate
import com.intellij.database.datagrid.nested.NestedTablesAware
import com.intellij.database.datagrid.nested.NestedTablesAware.NonEmptyStack

/**
 * Represents a model for sorting nested tables in a grid.
 *
 * This class implements the [GridSortingModel] interface and the [NestedTablesAware] interface.
 *
 * @param T The type of the sorting model to use for each nested table.
 * @property getModel A lambda function that creates the sorting model for each nested table.
 * @property sortingModelStack A non-empty stack data structure that holds the sorting models for the nested tables.
 * @constructor Creates a NestedTablesSortingModel with the specified getModel lambda function.
 */
class NestedTablesSortingModel<T : GridSortingModel<GridRow, GridColumn>>(
  private val getModel: () -> T
) : GridSortingModel<GridRow, GridColumn>, NestedTablesAware<T> {
  private val sortingModelStack: NonEmptyStack<T> = NonEmptyStack(getModel())

  private val activeSortingModel: T
    get() = sortingModelStack.last()

  override suspend fun enterNestedTable(coordinate: NestedTableCellCoordinate, nestedTable: NestedTable): T {
    sortingModelStack.push(getModel())
    return sortingModelStack.last()
  }

  override suspend fun exitNestedTable(steps: Int): T = sortingModelStack.pop(steps)

  override fun isSortingEnabled(): Boolean = activeSortingModel.isSortingEnabled

  override fun setSortingEnabled(enabled: Boolean): Unit = activeSortingModel.setSortingEnabled(enabled)

  override fun getOrdering(): MutableList<RowSortOrder<ModelIndex<GridColumn>>> = activeSortingModel.ordering

  override fun getAppliedOrdering(): MutableList<RowSortOrder<ModelIndex<GridColumn>>> = activeSortingModel.appliedOrdering

  override fun getAppliedSortingText(): String = activeSortingModel.appliedSortingText

  override fun apply(): Unit = activeSortingModel.apply()

  override fun getDocument(): Document? = activeSortingModel.document

  override fun getHistory(): MutableList<String> = activeSortingModel.history

  override fun setHistory(history: MutableList<String>): Unit = activeSortingModel.setHistory(history)

  override fun addListener(l: GridSortingModel.Listener, disposable: Disposable): Unit = activeSortingModel.addListener(l, disposable)

  override fun supportsAdditiveSorting(): Boolean = activeSortingModel.supportsAdditiveSorting()

  override fun setOrdering(ordering: MutableList<RowSortOrder<ModelIndex<GridColumn>>>): Unit = activeSortingModel.setOrdering(ordering)
}