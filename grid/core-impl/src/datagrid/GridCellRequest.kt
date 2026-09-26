package com.intellij.database.datagrid

import com.intellij.database.run.ui.DataAccessType

interface GridCellRequest<Row, Column> {
  val grid: CoreGrid<Row, Column>
  val rowIdx: ModelIndex<Row>
  val columnIdx: ModelIndex<Column>

  fun getColumn(): Column?
  fun getValue(): Any?

  fun isValid(): Boolean =
    isRowIdxValid() && isColumnIdxValid()

  fun isColumnIdxValid(): Boolean =
    columnIdx.isValid(grid)

  fun isRowIdxValid(): Boolean =
    rowIdx.isValid(grid)


  companion object {
    fun <Row, Column> request(grid: CoreGrid<Row, Column>, rowIdx: ModelIndex<Row>, columnIdx: ModelIndex<Column>): ActualGridCellRequest<Row, Column> =
      Impl(grid, rowIdx, columnIdx)
    fun <Row, Column> fixedValue(delegate: GridCellRequest<Row, Column>, valueOverride: Any?): FixedGridCellRequest<Row, Column> =
      FixedValue(delegate, valueOverride)
  }

  private class Impl<Row, Column> constructor(
    override val grid: CoreGrid<Row, Column>, override val rowIdx: ModelIndex<Row>, override val columnIdx: ModelIndex<Column>
  ): ActualGridCellRequest<Row, Column> {
    override fun getColumn(): Column? =
      grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(columnIdx)

    override fun getValue(): Any? =
      grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getValueAt(rowIdx, columnIdx)
  }

  private class FixedValue<Row, Column> constructor(
    private val delegate: GridCellRequest<Row, Column>, private val valueOverride: Any?
  ) : FixedGridCellRequest<Row, Column>, GridCellRequest<Row, Column> by delegate {
    override fun getValue(): Any? =
      valueOverride
  }
}

interface ActualGridCellRequest<Row, Column>: GridCellRequest<Row, Column>
interface FixedGridCellRequest<Row, Column>: GridCellRequest<Row, Column>

fun <Row, Column> CoreGrid<Row, Column>.requestColumn(columnIdx: ModelIndex<Column>): ActualGridCellRequest<Row, Column> =
  request(ModelIndex.forRow(this, -1), columnIdx)

fun <Row, Column> CoreGrid<Row, Column>.request(rowIdx: ModelIndex<Row>, columnIdx: ModelIndex<Column>): ActualGridCellRequest<Row, Column> =
  GridCellRequest.request(this, rowIdx, columnIdx)

fun <Row, Column> CoreGrid<Row, Column>.selectedCellRequest(): ActualGridCellRequest<Row, Column> =
  request(selectionModel.leadSelectionRow, selectionModel.leadSelectionColumn)

fun <Row, Column> GridCellRequest<Row, Column>.overrideValue(value: Any?): FixedGridCellRequest<Row, Column> =
  GridCellRequest.fixedValue(this, value)

fun <Row, Column> GridCellRequest<Row, Column>.actual(): ActualGridCellRequest<Row, Column> =
  this as? ActualGridCellRequest<Row, Column> ?: GridCellRequest.request(grid, rowIdx, columnIdx)

/** Pre-mutation DB value; [actual] only strips an [overrideValue] wrapper, this also bypasses the mutation overlay. */
val <Row, Column> GridCellRequest<Row, Column>.databaseValue: Any?
  get() = grid.getDataModel(DataAccessType.DATABASE_DATA).getValueAt(rowIdx, columnIdx)

fun <Row, Column> GridCellRequest<Row, Column>.fixed(): FixedGridCellRequest<Row, Column> =
  this as? FixedGridCellRequest<Row, Column> ?: overrideValue(getValue())
