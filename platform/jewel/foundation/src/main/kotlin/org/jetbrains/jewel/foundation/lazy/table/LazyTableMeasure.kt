// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastForEach
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope

@Suppress("NestedBlockDepth", "UnusedParameter")
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
    placementScopeInvalidator: ObservableScopeInvalidator,
    coroutineScope: CoroutineScope,
    density: Density,
    hasLookaheadOccurred: Boolean,
    isLookingAhead: Boolean,
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
            remeasureNeeded = false,
            coroutineScope = coroutineScope,
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
            density = density,
            scrollBackAmount = Offset.Zero,
            rowPrefetchInfoRetriever = { emptyList() },
            columnPrefetchInfoRetriever = { emptyList() },
        )
    } else {
        var scrollDeltaX = scrollToBeConsumed.x.roundToInt()
        var scrollDeltaY = scrollToBeConsumed.y.roundToInt()

        var currentFirstFloatingColumn = firstVisibleCellPosition.x
        var currentFirstFloatingRow = firstVisibleCellPosition.y

        var currentFirstFloatingColumnScrollOffset = firstVisibleCellScrollOffset.x
        var currentFirstFloatingRowScrollOffset = firstVisibleCellScrollOffset.y

        // Track if items were composed but won't be in final viewport
        var remeasureNeeded = false

        // Track initial positions to detect corrections
        val requestedFirstColumn = currentFirstFloatingColumn
        val requestedFirstRow = currentFirstFloatingRow

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

        // Check if we had to correct the requested position
        val correctedInitialPosition =
            currentFirstFloatingColumn != requestedFirstColumn || currentFirstFloatingRow != requestedFirstRow

        if (correctedInitialPosition) {
            // Starting position was invalid, measurement may have been wasted
            remeasureNeeded = true
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

                    // Column was composed but immediately scrolled past
                    remeasureNeeded = true
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

                    // Row was composed but immediately scrolled past
                    remeasureNeeded = true
                }
            }
        } while (needNextColumn || needNextRow)

        val preScrollBackScrollDeltaX = scrollDeltaX
        val preScrollBackScrollDeltaY = scrollDeltaY

        if (currentCellAxisOffsetX < maxOffsetX) {
            val toScrollBack = maxOffsetX - currentCellAxisOffsetX
            currentFirstFloatingColumnScrollOffset -= toScrollBack
            currentCellAxisOffsetX += toScrollBack

            // Track if we enter the scroll-back loop
            val hadToScrollBackHorizontally =
                currentCellAxisOffsetX < maxOffsetX && currentFirstFloatingColumn > pinnedColumns

            while (currentFirstFloatingColumnScrollOffset < 0 && currentFirstFloatingColumn > pinnedColumns) {
                val column = currentFirstFloatingColumn - 1
                val columnWidth = measuredItemProvider.getColumnWidth(column)

                currentFirstFloatingColumnScrollOffset += columnWidth + horizontalSpacing
                currentFirstFloatingColumn = column
            }

            // If we scrolled back, we composed extra columns to fill viewport
            if (hadToScrollBackHorizontally) {
                remeasureNeeded = true
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

            // Track if we enter the scroll-back loop
            val hadToScrollBackVertically = currentCellAxisOffsetY < maxOffsetY && currentFirstFloatingRow > pinnedRows

            while (currentFirstFloatingRowScrollOffset < 0 && currentFirstFloatingRow > pinnedRows) {
                val row = currentFirstFloatingRow - 1
                val rowHeight = measuredItemProvider.getRowHeight(row)

                currentFirstFloatingRowScrollOffset += rowHeight + verticalSpacing
                currentFirstFloatingRow = row
            }

            // If we scrolled back, we composed extra rows to fill viewport
            if (hadToScrollBackVertically) {
                remeasureNeeded = true
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

        val unconsumedScrollX = scrollToBeConsumed.x - consumedScrollX
        val unconsumedScrollY = scrollToBeConsumed.y - consumedScrollY

        val scrollBackAmountX: Float =
            if (isLookingAhead && scrollDeltaX > preScrollBackScrollDeltaX && unconsumedScrollX <= 0) {
                scrollDeltaX - preScrollBackScrollDeltaX + unconsumedScrollX
            } else {
                0f
            }

        val scrollBackAmountY: Float =
            if (isLookingAhead && scrollDeltaY > preScrollBackScrollDeltaY && unconsumedScrollY <= 0) {
                scrollDeltaY - preScrollBackScrollDeltaY + unconsumedScrollY
            } else {
                0f
            }

        val scrollBackAmount = Offset(scrollBackAmountX, scrollBackAmountY)

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

        // Even thought this looks like four iterations on top of the same items, they do not overlap at all
        // Each loop runs the validations from one section:
        // - The first one measures the static items that never change (the combined section of pinned rows and columns)
        // - The second and third measure only the pinned rows and columns that can scroll
        // - The last one measures the floating items (items that are not on a pinned column or row)
        repeat(pinnedRows) { row ->
            repeat(pinnedColumns) { column -> pinnedItemsInfo += measuredItemProvider.getAndMeasure(column, row) }
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
            for (column in extraColumns) {
                val isColumnPinned = column < pinnedColumns

                if (!isColumnPinned) {
                    pinnedRowsInfo += measuredItemProvider.getAndMeasure(column, row)
                }
            }
        }

        for (row in extraRows) {
            for (column in extraColumns) {
                val isPinned = row < pinnedRows || column < pinnedColumns
                if (!isPinned) {
                    floatingItemsInfo += measuredItemProvider.getAndMeasure(column, row)
                }
            }
        }

        calculateItemsOffsets(
            measuredItemProvider = measuredItemProvider,
            floatingItems = floatingItemsInfo,
            pinnedColumnsItems = pinnedColumnsInfo,
            pinnedRowsItems = pinnedRowsInfo,
            pinnedItems = pinnedItemsInfo,
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

        // Create prefetch info retrievers
        val rowPrefetchInfoRetriever: (row: Int) -> List<Pair<Int, Constraints>> = { row ->
            val result = mutableListOf<Pair<Int, Constraints>>()
            // For the given row, return all visible columns with their constraints
            for (col in pinnedColumns until (pinnedColumns + extraColumnCount).coerceAtMost(columns)) {
                val cellConstraints = measuredItemProvider.getConstraintsFor(col, row)
                val index = row * columns + col
                result.add(index to cellConstraints)
            }
            result
        }

        val columnPrefetchInfoRetriever: (column: Int) -> List<Pair<Int, Constraints>> = { column ->
            val result = mutableListOf<Pair<Int, Constraints>>()
            // For the given column, return all visible rows with their constraints
            for (row in pinnedRows until (pinnedRows + extraRowCount).coerceAtMost(rows)) {
                val cellConstraints = measuredItemProvider.getConstraintsFor(column, row)
                val index = row * columns + column
                result.add(index to cellConstraints)
            }
            result
        }

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
            remeasureNeeded = remeasureNeeded,
            coroutineScope = coroutineScope,
            measureResult =
                layout(constraints.maxWidth, constraints.maxHeight) {
                    floatingItemsInfo.fastForEach { it.place(this) }

                    pinnedColumnsInfo.fastForEach { it.place(this, 1f) }

                    pinnedRowsInfo.fastForEach { it.place(this, 1f) }

                    pinnedItemsInfo.fastForEach { it.place(this, 1f) }

                    // we attach it during the placement so LazyTableState can trigger re-placement
                    placementScopeInvalidator.attachToScope()
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
            density = density,
            scrollBackAmount = scrollBackAmount,
            rowPrefetchInfoRetriever = rowPrefetchInfoRetriever,
            columnPrefetchInfoRetriever = columnPrefetchInfoRetriever,
        )
    }
}

private fun calculateItemsOffsets(
    measuredItemProvider: LazyTableMeasuredItemProvider,
    floatingItems: List<LazyTableMeasuredItem>,
    pinnedColumnsItems: List<LazyTableMeasuredItem>,
    pinnedRowsItems: List<LazyTableMeasuredItem>,
    pinnedItems: List<LazyTableMeasuredItem>,
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
    // Pre-calculate all offsets iteratively to avoid recursion and improve performance
    val columnOffsets = IntArray(if (extraColumns.isEmpty()) 0 else extraColumns.last + 1)
    val rowOffsets = IntArray(if (extraRows.isEmpty()) 0 else extraRows.last + 1)
    val pinnedColumnOffsets = IntArray(pinnedColumns)
    val pinnedRowOffsets = IntArray(pinnedRows)

    // Calculate pinned column offsets iteratively
    var offset = 0
    for (col in 0 until pinnedColumns) {
        pinnedColumnOffsets[col] = offset
        offset += measuredItemProvider.getColumnWidth(col) + horizontalSpacing
    }

    // Calculate pinned row offsets iteratively
    offset = 0
    for (row in 0 until pinnedRows) {
        pinnedRowOffsets[row] = offset
        offset += measuredItemProvider.getRowHeight(row) + verticalSpacing
    }

    // Calculate floating column offsets
    if (!extraColumns.isEmpty()) {
        // Start with the first visible cell position
        columnOffsets[firstVisibleCellPosition.x] = cellsScrollOffset.x + pinnedColumnsWidth

        // Forward pass: calculate offsets for columns after the first visible
        for (col in (firstVisibleCellPosition.x + 1)..extraColumns.last) {
            val prevWidth = measuredItemProvider.getColumnWidth(col - 1)
            columnOffsets[col] = columnOffsets[col - 1] + prevWidth + horizontalSpacing
        }

        // Backward pass: calculate offsets for columns before the first visible
        for (col in (extraColumns.first until firstVisibleCellPosition.x).reversed()) {
            val currentWidth = measuredItemProvider.getColumnWidth(col)
            columnOffsets[col] = columnOffsets[col + 1] - currentWidth - horizontalSpacing
        }
    }

    // Calculate floating row offsets
    if (!extraRows.isEmpty()) {
        // Start with the first visible cell position
        rowOffsets[firstVisibleCellPosition.y] = cellsScrollOffset.y + pinnedRowsHeight

        // Forward pass: calculate offsets for rows after the first visible
        for (row in (firstVisibleCellPosition.y + 1)..extraRows.last) {
            val prevHeight = measuredItemProvider.getRowHeight(row - 1)
            rowOffsets[row] = rowOffsets[row - 1] + prevHeight + verticalSpacing
        }

        // Backward pass: calculate offsets for rows before the first visible
        for (row in (extraRows.first until firstVisibleCellPosition.y).reversed()) {
            val currentHeight = measuredItemProvider.getRowHeight(row)
            rowOffsets[row] = rowOffsets[row + 1] - currentHeight - verticalSpacing
        }
    }

    // Helper functions for array lookup (replaces recursive functions)
    fun getScrollOffsetX(column: Int): Int =
        if (column < pinnedColumns) pinnedColumnOffsets[column] else columnOffsets[column]

    fun getScrollOffsetY(row: Int): Int = if (row < pinnedRows) pinnedRowOffsets[row] else rowOffsets[row]

    // Position floating items
    floatingItems.fastForEach { item ->
        val offsetX = getScrollOffsetX(item.column)
        val offsetY = getScrollOffsetY(item.row)
        item.position(IntOffset(offsetX, offsetY))
    }

    // Position pinned items
    pinnedItems.fastForEach { item ->
        val offsetX = getScrollOffsetX(item.column)
        val offsetY = getScrollOffsetY(item.row)
        item.position(IntOffset(offsetX, offsetY))
    }

    // Position pinned rows items
    pinnedRowsItems.fastForEach { item ->
        val offsetX = getScrollOffsetX(item.column)
        val offsetY = getScrollOffsetY(item.row)
        item.position(IntOffset(offsetX, offsetY))
    }

    // Position pinned columns items
    pinnedColumnsItems.fastForEach { item ->
        val offsetX = getScrollOffsetX(item.column)
        val offsetY = getScrollOffsetY(item.row)
        item.position(IntOffset(offsetX, offsetY))
    }
}
