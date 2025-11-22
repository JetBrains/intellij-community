// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastForEach
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

@Suppress("NestedBlockDepth")
internal fun measureLazyTable(
    constraints: Constraints,
    availableSize: IntSize,
    rows: Int,
    columns: Int,
    pinnedColumns: Int,
    pinnedRows: Int,
    measuredItemProvider: LazyTableMeasuredItemProvider,
    horizontalSpacing: Int,
    verticalSpacing: Int,
    firstVisibleCellPosition: IntOffset,
    firstVisibleCellScrollOffset: IntOffset,
    scrollToBeConsumed: Offset,
    beyondBoundsItemCount: Int,
    layout: (Int, Int, Placeable.PlacementScope.() -> Unit) -> MeasureResult,
): LazyTableMeasureResult {
    if (rows * columns <= 0) {
        return LazyTableMeasureResult(
            firstFloatingCell = null,
            firstFloatingCellScrollOffset = IntOffset.Zero,
            canVerticalScrollForward = false,
            canVerticalScrollBackward = false,
            consumedVerticalScroll = 0f,
            canHorizontalScrollForward = false,
            canHorizontalScrollBackward = false,
            consumedHorizontalScroll = 0f,
            measureResult = layout(constraints.minWidth, constraints.minHeight) {},
            floatingItemsInfo = emptyList(),
            pinnedColumnsInfo = emptyList(),
            pinnedRowsInfo = emptyList(),
            pinnedItemsInfo = emptyList(),
            viewportStartOffset = IntOffset.Zero,
            viewportEndOffset = IntOffset.Zero,
            viewportCellSize = IntSize.Zero,
            columns = columns,
            rows = rows,
            pinnedColumns = 0,
            pinnedRows = 0,
            pinnedColumnsWidth = 0,
            pinnedRowsHeight = 0,
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
        )
    } else {
        var scrollDeltaX = scrollToBeConsumed.x.roundToInt()
        var scrollDeltaY = scrollToBeConsumed.y.roundToInt()

        var currentFirstFloatingColumn = firstVisibleCellPosition.x
        var currentFirstFloatingRow = firstVisibleCellPosition.y

        var currentFirstFloatingColumnScrollOffset = firstVisibleCellScrollOffset.x
        var currentFirstFloatingRowScrollOffset = firstVisibleCellScrollOffset.y

        if (currentFirstFloatingColumn >= columns) {
            currentFirstFloatingColumn = columns - 1
            currentFirstFloatingColumnScrollOffset = 0
        }

        if (currentFirstFloatingColumn < pinnedColumns) {
            currentFirstFloatingColumn = pinnedColumns
            currentFirstFloatingColumnScrollOffset = 0
        }

        if (currentFirstFloatingRow >= rows) {
            currentFirstFloatingRow = rows - 1
            currentFirstFloatingRowScrollOffset = 0
        }

        if (currentFirstFloatingRow < pinnedRows) {
            currentFirstFloatingRow = pinnedRows
            currentFirstFloatingRowScrollOffset = 0
        }

        currentFirstFloatingColumnScrollOffset -= scrollDeltaX
        currentFirstFloatingRowScrollOffset -= scrollDeltaY

        if (currentFirstFloatingColumn == pinnedColumns && currentFirstFloatingColumnScrollOffset < 0) {
            scrollDeltaX += currentFirstFloatingColumnScrollOffset
            currentFirstFloatingColumnScrollOffset = 0
        }

        if (currentFirstFloatingRow == pinnedRows && currentFirstFloatingRowScrollOffset < 0) {
            scrollDeltaY += currentFirstFloatingRowScrollOffset
            currentFirstFloatingRowScrollOffset = 0
        }

        // measuredItemProvider.getAndMeasure(0, 0)

        val minOffsetX = 0
        var pinnedColumnsWidth = 0
        repeat(pinnedColumns) {
            val columnWidth = measuredItemProvider.getColumnWidth(it)
            pinnedColumnsWidth += columnWidth + horizontalSpacing
        }
        val maxOffsetX = availableSize.width - pinnedColumnsWidth

        val minOffsetY = 0
        var pinnedRowsHeight = 0
        repeat(pinnedRows) {
            val rowHeight = measuredItemProvider.getRowHeight(it)
            pinnedRowsHeight += rowHeight + verticalSpacing
        }
        val maxOffsetY = availableSize.height - pinnedRowsHeight

        do {
            val needPreviousColumn =
                currentFirstFloatingColumnScrollOffset < 0 && currentFirstFloatingColumn > pinnedColumns
            val needPreviousRow = currentFirstFloatingRowScrollOffset < 0 && currentFirstFloatingRow > pinnedRows

            if (needPreviousColumn) {
                val column = currentFirstFloatingColumn - 1
                val columnWidth = measuredItemProvider.getColumnWidth(column)

                currentFirstFloatingColumnScrollOffset += columnWidth + horizontalSpacing
                currentFirstFloatingColumn = column
            }

            if (needPreviousRow) {
                val row = currentFirstFloatingRow - 1
                val rowHeight = measuredItemProvider.getRowHeight(row)

                currentFirstFloatingRowScrollOffset += rowHeight + verticalSpacing
                currentFirstFloatingRow = row
            }
        } while (needPreviousColumn || needPreviousRow)

        if (currentFirstFloatingColumnScrollOffset < minOffsetX) {
            scrollDeltaX -= (minOffsetX - currentFirstFloatingColumnScrollOffset)
            currentFirstFloatingColumnScrollOffset = minOffsetX
        }

        if (currentFirstFloatingRowScrollOffset < minOffsetY) {
            scrollDeltaY -= (minOffsetY - currentFirstFloatingRowScrollOffset)
            currentFirstFloatingRowScrollOffset = minOffsetY
        }

        var currentLastFloatingColumn = currentFirstFloatingColumn
        var currentLastFloatingRow = currentFirstFloatingRow

        var currentCellAxisOffsetX = -currentFirstFloatingColumnScrollOffset
        var currentCellAxisOffsetY = -currentFirstFloatingRowScrollOffset

        do {
            val needNextColumn =
                currentLastFloatingColumn < columns &&
                    (currentCellAxisOffsetX < maxOffsetX || currentCellAxisOffsetX <= 0)
            val needNextRow =
                currentLastFloatingRow < rows && (currentCellAxisOffsetY < maxOffsetY || currentCellAxisOffsetY <= 0)

            if (needNextColumn) {
                val columnWidth = measuredItemProvider.getColumnWidth(currentLastFloatingColumn)
                currentCellAxisOffsetX += columnWidth + horizontalSpacing
                currentLastFloatingColumn++

                if (currentLastFloatingColumn >= columns) {
                    currentCellAxisOffsetX -= horizontalSpacing
                }

                if (currentCellAxisOffsetX <= minOffsetX && currentLastFloatingColumn < columns - 1) {
                    currentFirstFloatingColumn = currentLastFloatingColumn
                    currentFirstFloatingColumnScrollOffset -= (columnWidth + horizontalSpacing)
                }
            }

            if (needNextRow) {
                val rowHeight = measuredItemProvider.getRowHeight(currentLastFloatingRow)
                currentCellAxisOffsetY += rowHeight + verticalSpacing
                currentLastFloatingRow++

                if (currentLastFloatingRow >= rows) {
                    currentCellAxisOffsetY -= verticalSpacing
                }

                if (currentCellAxisOffsetY <= minOffsetY && currentLastFloatingRow < rows - 1) {
                    currentFirstFloatingRow = currentLastFloatingRow
                    currentFirstFloatingRowScrollOffset -= (rowHeight + verticalSpacing)
                }
            }
        } while (needNextColumn || needNextRow)

        if (currentCellAxisOffsetX < maxOffsetX) {
            val toScrollBack = maxOffsetX - currentCellAxisOffsetX
            currentFirstFloatingColumnScrollOffset -= toScrollBack
            currentCellAxisOffsetX += toScrollBack

            while (currentFirstFloatingColumnScrollOffset < 0 && currentFirstFloatingColumn > pinnedColumns) {
                val column = currentFirstFloatingColumn - 1
                val columnWidth = measuredItemProvider.getColumnWidth(column)

                currentFirstFloatingColumnScrollOffset += columnWidth + horizontalSpacing
                currentFirstFloatingColumn = column
            }
            scrollDeltaX += toScrollBack
            if (currentFirstFloatingColumnScrollOffset < 0) {
                scrollDeltaX += currentFirstFloatingColumnScrollOffset
                currentCellAxisOffsetX += currentFirstFloatingColumnScrollOffset
                currentFirstFloatingColumnScrollOffset = 0
            }
        }

        if (currentCellAxisOffsetY < maxOffsetY) {
            val toScrollBack = maxOffsetY - currentCellAxisOffsetY
            currentFirstFloatingRowScrollOffset -= toScrollBack
            currentCellAxisOffsetY += toScrollBack

            while (currentFirstFloatingRowScrollOffset < 0 && currentFirstFloatingRow > pinnedRows) {
                val row = currentFirstFloatingRow - 1
                val rowHeight = measuredItemProvider.getRowHeight(row)

                currentFirstFloatingRowScrollOffset += rowHeight + verticalSpacing
                currentFirstFloatingRow = row
            }
            scrollDeltaY += toScrollBack
            if (currentFirstFloatingRowScrollOffset < 0) {
                scrollDeltaY += currentFirstFloatingRowScrollOffset
                currentCellAxisOffsetY += currentFirstFloatingRowScrollOffset
                currentFirstFloatingRowScrollOffset = 0
            }
        }

        val consumedScrollX =
            if (
                scrollToBeConsumed.x.roundToInt().sign == scrollDeltaX.sign &&
                    abs(scrollToBeConsumed.x.roundToInt()) >= abs(scrollDeltaX)
            ) {
                scrollDeltaX.toFloat()
            } else {
                scrollToBeConsumed.x
            }

        val consumedScrollY =
            if (
                scrollToBeConsumed.y.roundToInt().sign == scrollDeltaY.sign &&
                    abs(scrollToBeConsumed.y.roundToInt()) >= abs(scrollDeltaY)
            ) {
                scrollDeltaY.toFloat()
            } else {
                scrollToBeConsumed.y
            }

        require(currentFirstFloatingColumnScrollOffset >= 0 && currentFirstFloatingRowScrollOffset >= 0) {
            "Invalid scroll offset: $currentFirstFloatingColumnScrollOffset, $currentFirstFloatingRowScrollOffset"
        }

        val startCellPositionX = max(pinnedColumns, currentFirstFloatingColumn - beyondBoundsItemCount)
        val endCellPositionX = min(columns, currentLastFloatingColumn + beyondBoundsItemCount)

        val startCellPositionY = max(pinnedRows, currentFirstFloatingRow - beyondBoundsItemCount)
        val endCellPositionY = min(rows, currentLastFloatingRow + beyondBoundsItemCount)

        val visibleRowCount = currentLastFloatingRow - currentFirstFloatingRow
        val visibleColumnCount = currentLastFloatingColumn - currentFirstFloatingColumn

        val extraRows = startCellPositionY until endCellPositionY
        val extraColumns = startCellPositionX until endCellPositionX

        val extraRowCount = endCellPositionY - startCellPositionY
        val extraColumnCount = endCellPositionX - startCellPositionX

        // floating items
        val floatingItemsInfo = ArrayList<LazyTableMeasuredItem>(extraRowCount * extraColumnCount)
        // floating rows but pinned columns
        val pinnedColumnsInfo = ArrayList<LazyTableMeasuredItem>(extraRowCount * pinnedColumns)
        // floating columns but pinned rows
        val pinnedRowsInfo = ArrayList<LazyTableMeasuredItem>(extraColumnCount * pinnedRows)
        // pinned items
        val pinnedItemsInfo = ArrayList<LazyTableMeasuredItem>(pinnedColumns * pinnedRows)

        for (row in extraRows) {
            for (column in extraColumns) {
                val isPinned = row < pinnedRows || column < pinnedColumns
                if (!isPinned) {
                    floatingItemsInfo += measuredItemProvider.getAndMeasure(column, row)
                }
            }
        }

        repeat(pinnedRows) { row ->
            for (column in extraColumns) {
                val isColumnPinned = column < pinnedColumns

                if (!isColumnPinned) {
                    pinnedRowsInfo += measuredItemProvider.getAndMeasure(column, row)
                }
            }
        }

        repeat(pinnedColumns) { column ->
            for (row in extraRows) {
                val isRowPinned = row < pinnedRows

                if (!isRowPinned) {
                    pinnedColumnsInfo += measuredItemProvider.getAndMeasure(column, row)
                }
            }
        }

        repeat(pinnedRows) { row ->
            repeat(pinnedColumns) { column -> pinnedItemsInfo += measuredItemProvider.getAndMeasure(column, row) }
        }

        calculateItemsOffsets(
            measuredItemProvider = measuredItemProvider,
            firstVisibleCellPosition = IntOffset(currentFirstFloatingColumn, currentFirstFloatingRow),
            cellsScrollOffset =
                IntOffset(-currentFirstFloatingColumnScrollOffset, -currentFirstFloatingRowScrollOffset),
            pinnedColumns = pinnedColumns,
            pinnedRows = pinnedRows,
            pinnedColumnsWidth = pinnedColumnsWidth,
            pinnedRowsHeight = pinnedRowsHeight,
            extraColumns = extraColumns,
            extraRows = extraRows,
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
        )

        return LazyTableMeasureResult(
            firstFloatingCell =
                measuredItemProvider.getAndMeasureOrNull(currentFirstFloatingColumn, currentFirstFloatingRow),
            firstFloatingCellScrollOffset =
                IntOffset(currentFirstFloatingColumnScrollOffset, currentFirstFloatingRowScrollOffset),
            canVerticalScrollForward = currentLastFloatingRow < rows || currentCellAxisOffsetY > maxOffsetY,
            canVerticalScrollBackward = currentFirstFloatingRowScrollOffset > 0 || currentFirstFloatingRow > 0,
            consumedVerticalScroll = consumedScrollY,
            canHorizontalScrollForward = currentLastFloatingColumn < columns || currentCellAxisOffsetX > maxOffsetX,
            canHorizontalScrollBackward = currentFirstFloatingColumnScrollOffset > 0 || currentFirstFloatingColumn > 0,
            consumedHorizontalScroll = consumedScrollX,
            measureResult =
                layout(constraints.maxWidth, constraints.maxHeight) {
                    floatingItemsInfo.fastForEach { it.place(this) }

                    pinnedColumnsInfo.fastForEach { it.place(this, 1f) }

                    pinnedRowsInfo.fastForEach { it.place(this, 1f) }

                    pinnedItemsInfo.fastForEach { it.place(this, 1f) }
                },
            floatingItemsInfo = floatingItemsInfo,
            pinnedColumnsInfo = pinnedColumnsInfo,
            pinnedRowsInfo = pinnedRowsInfo,
            pinnedItemsInfo = pinnedItemsInfo,
            viewportStartOffset = IntOffset.Zero,
            viewportEndOffset = IntOffset(maxOffsetX, maxOffsetY),
            viewportCellSize = IntSize(visibleColumnCount, visibleRowCount),
            columns = columns,
            rows = rows,
            pinnedColumns = pinnedColumns,
            pinnedRows = pinnedRows,
            pinnedColumnsWidth = pinnedColumnsWidth,
            pinnedRowsHeight = pinnedRowsHeight,
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
        )
    }
}

private fun calculateItemsOffsets(
    measuredItemProvider: LazyTableMeasuredItemProvider,
    firstVisibleCellPosition: IntOffset,
    cellsScrollOffset: IntOffset,
    pinnedRows: Int,
    pinnedColumns: Int,
    pinnedColumnsWidth: Int,
    pinnedRowsHeight: Int,
    extraRows: IntRange,
    extraColumns: IntRange,
    horizontalSpacing: Int,
    verticalSpacing: Int,
) {
    val positionedColumns = HashMap<Int, Int>(extraColumns.last - extraColumns.first + 1 + pinnedColumns)
    val positionedRows = HashMap<Int, Int>(extraRows.last - extraRows.first + 1 + pinnedRows)
    val positionedPinnedColumns = HashMap<Int, Int>(pinnedColumns)
    val positionedPinnedRows = HashMap<Int, Int>(pinnedRows)

    fun getCellScrollOffsetX(column: Int): Int =
        positionedColumns.getOrPut(column) {
            if (column > firstVisibleCellPosition.x) {
                val previousColumn = column - 1
                val previousColumnWidth = measuredItemProvider.getColumnWidth(previousColumn)
                val previousColumnScrollOffset = getCellScrollOffsetX(previousColumn)

                previousColumnScrollOffset + previousColumnWidth + horizontalSpacing
            } else if (column < firstVisibleCellPosition.x) {
                val currentWidth = measuredItemProvider.getColumnWidth(column)
                val nextColumnScrollOffset = getCellScrollOffsetX(column + 1)

                nextColumnScrollOffset - currentWidth - horizontalSpacing
            } else {
                cellsScrollOffset.x + pinnedColumnsWidth
            }
        }

    fun getCellScrollOffsetY(row: Int): Int =
        positionedRows.getOrPut(row) {
            if (row > firstVisibleCellPosition.y) {
                val previousRow = row - 1
                val previousRowHeight = measuredItemProvider.getRowHeight(previousRow)
                val previousRowScrollOffset = getCellScrollOffsetY(previousRow)

                previousRowScrollOffset + previousRowHeight + verticalSpacing
            } else if (row < firstVisibleCellPosition.y) {
                val currentHeight = measuredItemProvider.getRowHeight(row)
                val nextRowScrollOffset = getCellScrollOffsetY(row + 1)

                nextRowScrollOffset - currentHeight - verticalSpacing
            } else {
                cellsScrollOffset.y + pinnedRowsHeight
            }
        }

    fun getPinnedCellScrollOffsetX(column: Int): Int =
        positionedPinnedColumns.getOrPut(column) {
            if (column > 0) {
                val previousColumn = column - 1
                val previousColumnWidth = measuredItemProvider.getColumnWidth(previousColumn)
                val previousColumnScrollOffset = getPinnedCellScrollOffsetX(previousColumn)

                previousColumnScrollOffset + previousColumnWidth + horizontalSpacing
            } else {
                0
            }
        }

    fun getPinnedCellScrollOffsetY(row: Int): Int =
        positionedPinnedRows.getOrPut(row) {
            if (row > 0) {
                val previousRow = row - 1
                val previousRowHeight = measuredItemProvider.getRowHeight(previousRow)
                val previousRowScrollOffset = getPinnedCellScrollOffsetY(previousRow)

                previousRowScrollOffset + previousRowHeight + verticalSpacing
            } else {
                0
            }
        }

    fun getScrollOffsetX(column: Int): Int =
        if (column < pinnedColumns) {
            getPinnedCellScrollOffsetX(column)
        } else {
            getCellScrollOffsetX(column)
        }

    fun getScrollOffsetY(row: Int): Int =
        if (row < pinnedRows) {
            getPinnedCellScrollOffsetY(row)
        } else {
            getCellScrollOffsetY(row)
        }

    for (row in extraRows) {
        for (column in extraColumns) {
            val item = measuredItemProvider.getAndMeasure(column, row)
            val offsetX = getScrollOffsetX(column)
            val offsetY = getScrollOffsetY(row)

            item.position(IntOffset(offsetX, offsetY))
        }
    }

    repeat(pinnedRows) { row ->
        repeat(pinnedColumns) { column ->
            val item = measuredItemProvider.getAndMeasure(column, row)
            val offsetX = getScrollOffsetX(column)
            val offsetY = getScrollOffsetY(row)

            item.position(IntOffset(offsetX, offsetY))
        }
    }

    repeat(pinnedRows) { row ->
        for (column in extraColumns) {
            val item = measuredItemProvider.getAndMeasure(column, row)
            val offsetX = getScrollOffsetX(column)
            val offsetY = getScrollOffsetY(row)

            item.position(IntOffset(offsetX, offsetY))
        }
    }

    repeat(pinnedColumns) { column ->
        for (row in extraRows) {
            val item = measuredItemProvider.getAndMeasure(column, row)
            val offsetX = getScrollOffsetX(column)
            val offsetY = getScrollOffsetY(row)

            item.position(IntOffset(offsetX, offsetY))
        }
    }
}
