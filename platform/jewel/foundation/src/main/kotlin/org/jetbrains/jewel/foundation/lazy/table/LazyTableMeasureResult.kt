// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastForEach
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope

internal class LazyTableMeasureResult(
    val firstFloatingCell: LazyTableMeasuredItem?,
    val firstFloatingCellScrollOffset: IntOffset,
    val scrollBackAmount: Offset,
    val canVerticalScrollForward: Boolean,
    val canVerticalScrollBackward: Boolean,
    val consumedVerticalScroll: Float,
    val canHorizontalScrollForward: Boolean,
    val canHorizontalScrollBackward: Boolean,
    val consumedHorizontalScroll: Float,
    override val density: Density,
    val remeasureNeeded: Boolean,
    internal val coroutineScope: CoroutineScope,
    private val measureResult: MeasureResult,
    override val floatingItemsInfo: List<LazyTableMeasuredItem>,
    override val pinnedColumnsInfo: List<LazyTableMeasuredItem>,
    override val pinnedRowsInfo: List<LazyTableMeasuredItem>,
    override val pinnedItemsInfo: List<LazyTableMeasuredItem>,
    override val viewportStartOffset: IntOffset,
    override val viewportEndOffset: IntOffset,
    override val viewportCellSize: IntSize,
    override val columns: Int,
    override val rows: Int,
    override val pinnedColumns: Int,
    override val pinnedRows: Int,
    override val pinnedColumnsWidth: Int,
    override val pinnedRowsHeight: Int,
    override val horizontalSpacing: Int,
    override val verticalSpacing: Int,
    val rowPrefetchInfoRetriever: (row: Int) -> List<Pair<Int, Constraints>>,
    val columnPrefetchInfoRetriever: (column: Int) -> List<Pair<Int, Constraints>>,
) : LazyTableLayoutInfo, MeasureResult by measureResult {
    override val viewportSize: IntSize
        get() = IntSize(width, height)

    @Suppress("UnusedParameter")
    fun copyWithScrollDeltaWithoutRemeasure(delta: IntOffset, updateAnimations: Boolean): LazyTableMeasureResult? {
        @Suppress("ComplexCondition")
        if (
            remeasureNeeded ||
                floatingItemsInfo.isEmpty() ||
                firstFloatingCell == null ||
                (firstFloatingCellScrollOffset - delta) !in firstFloatingCell.size
        ) {
            return null
        }

        // Get first and last visible cells for boundary checks
        val first = floatingItemsInfo.first()
        val last = floatingItemsInfo.last()

        // Check if we're near pinned boundaries - these need special handling
        if (first.column < pinnedColumns || first.row < pinnedRows) {
            // First cell is adjacent to pinned area
            return null
        }

        // Check horizontal boundaries
        val canApplyHorizontal =
            if (delta.x < 0) {
                // Scrolling right (forward) - negative delta moves content left
                val deltaToFirstColumnChange =
                    first.offset.x + first.size.width - viewportStartOffset.x - pinnedColumnsWidth
                val deltaToLastColumnChange = last.offset.x + last.size.width - viewportEndOffset.x
                minOf(deltaToFirstColumnChange, deltaToLastColumnChange) > -delta.x
            } else if (delta.x > 0) {
                // Scrolling left (backward) - positive delta moves content right
                val deltaToFirstColumnChange = viewportStartOffset.x + pinnedColumnsWidth - first.offset.x
                val deltaToLastColumnChange = viewportEndOffset.x - last.offset.x
                minOf(deltaToFirstColumnChange, deltaToLastColumnChange) > delta.x
            } else {
                true // No horizontal scroll
            }

        // Check vertical boundaries
        val canApplyVertical =
            if (delta.y < 0) {
                // Scrolling down (forward) - negative delta moves content up
                val deltaToFirstRowChange =
                    first.offset.y + first.size.height - viewportStartOffset.y - pinnedRowsHeight
                val deltaToLastRowChange = last.offset.y + last.size.height - viewportEndOffset.y
                minOf(deltaToFirstRowChange, deltaToLastRowChange) > -delta.y
            } else if (delta.y > 0) {
                // Scrolling up (backward) - positive delta moves content down
                val deltaToFirstRowChange = viewportStartOffset.y + pinnedRowsHeight - first.offset.y
                val deltaToLastRowChange = viewportEndOffset.y - last.offset.y
                minOf(deltaToFirstRowChange, deltaToLastRowChange) > delta.y
            } else {
                true // No vertical scroll
            }

        // Only apply delta if both axes can handle it
        if (!canApplyHorizontal || !canApplyVertical) {
            return null
        }

        if (delta.x != 0) {
            pinnedRowsInfo.fastForEach { it.applyScrollDelta(delta.copy(y = 0)) }
        }

        if (delta.y != 0) {
            pinnedColumnsInfo.fastForEach { it.applyScrollDelta(delta.copy(x = 0)) }
        }

        floatingItemsInfo.fastForEach { it.applyScrollDelta(delta) }

        return LazyTableMeasureResult(
            firstFloatingCell = firstFloatingCell,
            firstFloatingCellScrollOffset = firstFloatingCellScrollOffset - delta,
            scrollBackAmount = Offset.Zero,
            canVerticalScrollForward = canVerticalScrollForward || delta.y > 0,
            canVerticalScrollBackward = canVerticalScrollBackward || delta.y < 0,
            consumedVerticalScroll = delta.y.toFloat(),
            canHorizontalScrollForward = canHorizontalScrollForward || delta.x > 0,
            canHorizontalScrollBackward = canHorizontalScrollBackward || delta.x < 0,
            consumedHorizontalScroll = delta.x.toFloat(),
            remeasureNeeded = remeasureNeeded,
            coroutineScope = coroutineScope,
            measureResult = measureResult,
            floatingItemsInfo = floatingItemsInfo,
            pinnedColumnsInfo = pinnedColumnsInfo,
            pinnedRowsInfo = pinnedRowsInfo,
            pinnedItemsInfo = pinnedItemsInfo,
            viewportStartOffset = viewportStartOffset,
            viewportEndOffset = viewportEndOffset,
            viewportCellSize = viewportCellSize,
            columns = columns,
            rows = rows,
            pinnedColumns = pinnedColumns,
            pinnedRows = pinnedRows,
            pinnedColumnsWidth = pinnedColumnsWidth,
            pinnedRowsHeight = pinnedRowsHeight,
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
            density = density,
            rowPrefetchInfoRetriever = rowPrefetchInfoRetriever,
            columnPrefetchInfoRetriever = columnPrefetchInfoRetriever,
        )
    }
}

internal val EmptyLazyTableLayoutInfo =
    LazyTableMeasureResult(
        measureResult =
            object : MeasureResult {
                override val width: Int = 0
                override val height: Int = 0

                @Suppress("PrimitiveInCollection") override val alignmentLines: Map<AlignmentLine, Int> = emptyMap()

                override fun placeChildren() = Unit
            },
        firstFloatingCell = null,
        firstFloatingCellScrollOffset = IntOffset.Zero,
        scrollBackAmount = Offset.Zero,
        canVerticalScrollForward = false,
        canVerticalScrollBackward = false,
        consumedVerticalScroll = 0f,
        canHorizontalScrollForward = false,
        canHorizontalScrollBackward = false,
        consumedHorizontalScroll = 0f,
        remeasureNeeded = false,
        coroutineScope = CoroutineScope(EmptyCoroutineContext),
        floatingItemsInfo = emptyList(),
        pinnedColumnsInfo = emptyList(),
        pinnedRowsInfo = emptyList(),
        pinnedItemsInfo = emptyList(),
        viewportStartOffset = IntOffset.Zero,
        viewportEndOffset = IntOffset.Zero,
        viewportCellSize = IntSize.Zero,
        columns = 0,
        rows = 0,
        pinnedColumns = 0,
        pinnedRows = 0,
        pinnedColumnsWidth = 0,
        pinnedRowsHeight = 0,
        horizontalSpacing = 0,
        verticalSpacing = 0,
        density = Density(1f),
        rowPrefetchInfoRetriever = { emptyList() },
        columnPrefetchInfoRetriever = { emptyList() },
    )

private operator fun IntSize.contains(offset: IntOffset) = offset.x in 0 until width && offset.y in 0 until height
