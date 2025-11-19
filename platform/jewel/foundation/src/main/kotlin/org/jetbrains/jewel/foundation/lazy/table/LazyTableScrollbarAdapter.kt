// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.v2.maxScrollOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlin.math.abs

internal abstract class LazyTableScrollbarAdapter : ScrollbarAdapter {
    // Implement the adapter in terms of "lines", which means either rows,
    // (for a vertically scrollable widget) or columns (for a horizontally
    // scrollable one).
    // For LazyList this translates directly to items; for LazyGrid, it
    // translates to rows/columns of items.

    class VisibleLine(val index: Int, val offset: Int)

    /** Return the first visible line, if any. */
    protected abstract fun firstVisibleLine(): VisibleLine?

    /** Return the total number of lines. */
    protected abstract fun totalLineCount(): Int

    /** The sum of content padding (before+after) on the scrollable axis. */
    protected abstract fun contentPadding(): Int

    /** Scroll immediately to the given line, and offset it by [scrollOffset] pixels. */
    protected abstract suspend fun snapToLine(lineIndex: Int, scrollOffset: Int)

    /** Scroll from the current position by the given amount of pixels. */
    protected abstract suspend fun scrollBy(value: Float)

    /** Return the average size (on the scrollable axis) of the visible lines. */
    protected abstract fun averageVisibleLineSize(): Double

    /** The spacing between lines. */
    protected abstract val lineSpacing: Int

    private val averageVisibleLineSize by derivedStateOf {
        if (totalLineCount() == 0) {
            0.0
        } else {
            averageVisibleLineSize()
        }
    }

    private val averageVisibleLineSizeWithSpacing
        get() = averageVisibleLineSize + lineSpacing

    override val scrollOffset: Double
        get() {
            val firstVisibleLine = firstVisibleLine()
            return if (firstVisibleLine == null) {
                0.0
            } else {
                firstVisibleLine.index * averageVisibleLineSizeWithSpacing - firstVisibleLine.offset
            }
        }

    override val contentSize: Double
        get() {
            val totalLineCount = totalLineCount()
            return averageVisibleLineSize * totalLineCount +
                lineSpacing * (totalLineCount - 1).coerceAtLeast(0) +
                contentPadding()
        }

    override suspend fun scrollTo(scrollOffset: Double) {
        val distance = scrollOffset - this@LazyTableScrollbarAdapter.scrollOffset

        // if we scroll less than viewport we need to use scrollBy function to avoid
        // undesirable scroll jumps (when an item size is different)
        //
        // if we scroll more than viewport we should immediately jump to this position
        // without recreating all items between the current and the new position
        if (abs(distance) <= viewportSize) {
            scrollBy(distance.toFloat())
        } else {
            snapTo(scrollOffset)
        }
    }

    private suspend fun snapTo(scrollOffset: Double) {
        val scrollOffsetCoerced = scrollOffset.coerceIn(0.0, maxScrollOffset)

        val index =
            (scrollOffsetCoerced / averageVisibleLineSizeWithSpacing)
                .toInt()
                .coerceAtLeast(0)
                .coerceAtMost(totalLineCount() - 1)

        val offset = (scrollOffsetCoerced - index * averageVisibleLineSizeWithSpacing).toInt().coerceAtLeast(0)

        snapToLine(lineIndex = index, scrollOffset = offset)
    }
}

internal class LazyTableHorizontalScrollbarAdapter(private val scrollState: LazyTableState) :
    LazyTableScrollbarAdapter() {
    override val viewportSize: Double
        get() = with(scrollState.layoutInfo) { viewportSize.width - pinnedColumnsWidth }.toDouble()

    override fun firstVisibleLine(): VisibleLine? {
        val item = scrollState.layoutInfo.floatingItemsInfo.firstOrNull() ?: return null
        return VisibleLine(
            index = item.column - scrollState.layoutInfo.pinnedColumns,
            offset = item.offset.x - scrollState.layoutInfo.pinnedColumnsWidth,
        )
    }

    override fun totalLineCount() =
        if (scrollState.layoutInfo.rows > 0) {
            scrollState.layoutInfo.columns - scrollState.layoutInfo.pinnedColumns
        } else {
            0
        }

    override fun contentPadding() = with(scrollState.layoutInfo) { 0 }

    override suspend fun snapToLine(lineIndex: Int, scrollOffset: Int) {
        scrollState.scrollToColumn(lineIndex, scrollOffset)
    }

    override suspend fun scrollBy(value: Float) {
        scrollState.horizontalScrollableState.scrollBy(value)
    }

    override fun averageVisibleLineSize(): Double {
        val first = scrollState.layoutInfo.floatingItemsInfo.firstOrNull() ?: return 0.0
        val last = scrollState.layoutInfo.floatingItemsInfo.lastOrNull() ?: return 0.0
        val count = last.column - first.column + 1

        return (last.offset.x + last.size.width - first.offset.x - (count - 1) * lineSpacing).toDouble() / count
    }

    override val lineSpacing
        get() = scrollState.layoutInfo.horizontalSpacing
}

internal class LazyTableVerticalScrollbarAdapter(private val scrollState: LazyTableState) :
    LazyTableScrollbarAdapter() {
    override val viewportSize: Double
        get() = with(scrollState.layoutInfo) { viewportSize.height - pinnedRowsHeight }.toDouble()

    override fun firstVisibleLine(): VisibleLine? {
        val item = scrollState.layoutInfo.floatingItemsInfo.firstOrNull() ?: return null
        return VisibleLine(
            index = item.row - scrollState.layoutInfo.pinnedRows,
            offset = item.offset.y - scrollState.layoutInfo.pinnedRowsHeight,
        )
    }

    override fun totalLineCount() =
        if (scrollState.layoutInfo.columns > 0) scrollState.layoutInfo.rows - scrollState.layoutInfo.pinnedRows else 0

    override fun contentPadding() = with(scrollState.layoutInfo) { 0 }

    override suspend fun snapToLine(lineIndex: Int, scrollOffset: Int) {
        scrollState.scrollToRow(lineIndex, scrollOffset)
    }

    override suspend fun scrollBy(value: Float) {
        scrollState.verticalScrollableState.scrollBy(value)
    }

    override fun averageVisibleLineSize(): Double {
        val first = scrollState.layoutInfo.floatingItemsInfo.firstOrNull() ?: return 0.0
        val last = scrollState.layoutInfo.floatingItemsInfo.lastOrNull() ?: return 0.0

        val count = last.row - first.row + 1

        return (last.offset.y + last.size.height - first.offset.y - (count - 1) * lineSpacing).toDouble() / count
    }

    override val lineSpacing
        get() = scrollState.layoutInfo.verticalSpacing
}

/**
 * Creates a [ScrollbarAdapter] bound to the horizontal axis of the provided [scrollState].
 *
 * Use with a horizontal scrollbar to control and reflect the table's horizontal scroll.
 */
public fun tableHorizontalScrollbarAdapter(scrollState: LazyTableState): ScrollbarAdapter =
    LazyTableHorizontalScrollbarAdapter(scrollState)

/**
 * Remembers a horizontal [ScrollbarAdapter] for the given [scrollState].
 *
 * The adapter is recreated only when [scrollState] changes.
 */
@Composable
public fun rememberTableHorizontalScrollbarAdapter(scrollState: LazyTableState): ScrollbarAdapter =
    remember(scrollState) { tableHorizontalScrollbarAdapter(scrollState) }

/**
 * Creates a [ScrollbarAdapter] bound to the vertical axis of the provided [scrollState].
 *
 * Use with a vertical scrollbar to control and reflect the table's vertical scroll.
 */
public fun tableVerticalScrollbarAdapter(scrollState: LazyTableState): ScrollbarAdapter =
    LazyTableVerticalScrollbarAdapter(scrollState)

/**
 * Remembers a vertical [ScrollbarAdapter] for the given [scrollState].
 *
 * The adapter is recreated only when [scrollState] changes.
 */
@Composable
public fun rememberTableVerticalScrollbarAdapter(scrollState: LazyTableState): ScrollbarAdapter =
    remember(scrollState) { tableVerticalScrollbarAdapter(scrollState) }
