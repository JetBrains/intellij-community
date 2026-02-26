// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table.draggable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastFirstOrNull
import org.jetbrains.jewel.foundation.lazy.draggable.LazyLayoutDraggingState
import org.jetbrains.jewel.foundation.lazy.table.LazyTableItemInfo
import org.jetbrains.jewel.foundation.lazy.table.LazyTableState

/**
 * Creates and remembers a [LazyTableRowDraggingState] that survives recompositions.
 *
 * @param tableState The state of the lazy table whose rows will be dragged.
 * @param itemCanMove Predicate invoked with a row key; return `true` to allow that row to be
 *   dragged.
 * @param onMove Callback invoked when the dragged row should swap with another. Receives the key of
 *   the row being dragged (`from`) and the key of the row it is replacing (`to`). Return `true` if
 *   the move was accepted, `false` to leave the order unchanged.
 */
@Composable
public fun rememberLazyTableRowDraggingState(
    tableState: LazyTableState,
    itemCanMove: (Any?) -> Boolean,
    onMove: (Any?, Any?) -> Boolean,
): LazyTableRowDraggingState {
    val currentItemCanMove by rememberUpdatedState(itemCanMove)
    val currentOnMove by rememberUpdatedState(onMove)

    return remember(tableState) {
        LazyTableRowDraggingState(
            tableState = tableState,
            itemCanMove = { currentItemCanMove(it) },
            onMove = { from, to -> currentOnMove(from, to) },
        )
    }
}

/**
 * Creates and remembers a [LazyTableColumnDraggingState] that survives recompositions.
 *
 * @param tableState The state of the lazy table whose columns will be dragged.
 * @param itemCanMove Predicate invoked with a column key; return `true` to allow that column to be
 *   dragged.
 * @param onMove Callback invoked when the dragged column should swap with another. Receives the key
 *   of the column being dragged (`from`) and the key of the column it is replacing (`to`). Return
 *   `true` if the move was accepted, `false` to leave the order unchanged.
 */
@Composable
public fun rememberLazyTableColumnDraggingState(
    tableState: LazyTableState,
    itemCanMove: (Any?) -> Boolean,
    onMove: (Any?, Any?) -> Boolean,
): LazyTableColumnDraggingState {
    val currentItemCanMove by rememberUpdatedState(itemCanMove)
    val currentOnMove by rememberUpdatedState(onMove)

    return remember(tableState) {
        LazyTableColumnDraggingState(
            tableState = tableState,
            itemCanMove = { currentItemCanMove(it) },
            onMove = { from, to -> currentOnMove(from, to) },
        )
    }
}

/**
 * Base drag-reorder state for a [LazyTableState].
 *
 * Concrete subclasses implement row-axis ([LazyTableRowDraggingState]) or column-axis
 * ([LazyTableColumnDraggingState]) reordering by overriding the key/index projections and the
 * swap-threshold logic in [getReplacingItem].
 *
 * @param tableState The table whose layout info is consulted to locate items under the pointer.
 * @param itemCanMove Predicate that gates whether a given row/column key may be dragged.
 * @param onMove Callback that applies the reorder in the backing data source. Return `true` if the
 *   swap was performed, `false` to abort.
 */
public abstract class LazyTableDraggableState(
    public val tableState: LazyTableState,
    public val itemCanMove: (Any?) -> Boolean,
    public val onMove: (Any?, Any?) -> Boolean,
) : LazyLayoutDraggingState<LazyTableItemInfo>() {
    /**
     * Returns the [LazyTableItemInfo] whose bounding box contains [offset], searching pinned
     * columns, pinned rows, and pinned corner items in that order, or `null` if no item matches.
     */
    protected fun getItemAt(offset: Offset): LazyTableItemInfo? =
        tableState.layoutInfo.pinnedColumnsInfo.fastFirstOrNull {
            offset in Rect(it.offset.toOffset(), it.size.toSize())
        }
            ?: tableState.layoutInfo.pinnedRowsInfo.fastFirstOrNull {
                offset in Rect(it.offset.toOffset(), it.size.toSize())
            }
            ?: tableState.layoutInfo.pinnedItemsInfo.fastFirstOrNull {
                offset in Rect(it.offset.toOffset(), it.size.toSize())
            }

    override val LazyTableItemInfo.size: Size
        get() = this.size.toSize()

    override val LazyTableItemInfo.offset: Offset
        get() = this.offset.toOffset()

    override fun canMove(key: Any?): Boolean = itemCanMove(key)

    override fun moveItem(from: Any?, to: Any?): Boolean = onMove(from, to)
}

/**
 * Drag-reorder state that operates on table **rows**.
 *
 * A row swap is triggered when the dragged row's leading or trailing edge crosses the midpoint of
 * an adjacent row. The row key is the second element of the `Pair<columnKey, rowKey>` composite key
 * stored in [LazyTableItemInfo].
 *
 * Prefer creating instances via [rememberLazyTableRowDraggingState].
 */
public class LazyTableRowDraggingState(
    tableState: LazyTableState,
    itemCanMove: (Any?) -> Boolean,
    onMove: (Any?, Any?) -> Boolean,
) : LazyTableDraggableState(tableState, itemCanMove, onMove) {
    override val LazyTableItemInfo.index: Int
        get() = this.row

    override val LazyTableItemInfo.key: Any?
        get() = (this.key as Pair<Any?, Any?>?)?.second

    override fun getItemWithKey(key: Any): LazyTableItemInfo? = findPinnedItemByKey(tableState, key) { it?.second }

    override fun getReplacingItem(draggingItem: LazyTableItemInfo): LazyTableItemInfo? {
        if (draggingItemOffsetTransformY > 0) {
            val bottomBorder = draggingItem.offset.y + draggingItem.size.height + draggingItemOffsetTransformY
            val replacingItem = getItemAt(initialOffset.copy(y = bottomBorder)) ?: return null
            val topBorder = replacingItem.offset.y + (replacingItem.size.height / 2)
            if (bottomBorder >= topBorder) return replacingItem
        } else {
            val topBorder = draggingItem.offset.y + draggingItemOffsetTransformY
            val replacingItem = getItemAt(initialOffset.copy(y = topBorder)) ?: return null
            val bottomBorder = replacingItem.offset.y + (replacingItem.size.height / 2)
            if (bottomBorder >= topBorder) return replacingItem
        }
        return null
    }
}

/**
 * Drag-reorder state that operates on table **columns**.
 *
 * A column swap is triggered when the dragged column's leading or trailing edge crosses the
 * midpoint of an adjacent column. The column key is the first element of the
 * `Pair<columnKey, rowKey>` composite key stored in [LazyTableItemInfo].
 *
 * Prefer creating instances via [rememberLazyTableColumnDraggingState].
 */
public class LazyTableColumnDraggingState(
    tableState: LazyTableState,
    itemCanMove: (Any?) -> Boolean,
    onMove: (Any?, Any?) -> Boolean,
) : LazyTableDraggableState(tableState, itemCanMove, onMove) {
    override val LazyTableItemInfo.index: Int
        get() = this.column

    override val LazyTableItemInfo.key: Any?
        get() = (this.key as Pair<Any?, Any?>?)?.first

    override fun getItemWithKey(key: Any): LazyTableItemInfo? = findPinnedItemByKey(tableState, key) { it?.first }

    override fun getReplacingItem(draggingItem: LazyTableItemInfo): LazyTableItemInfo? {
        if (draggingItemOffsetTransformX > 0) {
            val rightBorder = draggingItem.offset.x + draggingItem.size.width + draggingItemOffsetTransformX
            val replacingItem = getItemAt(initialOffset.copy(x = rightBorder)) ?: return null
            val leftBorder = replacingItem.offset.x + (replacingItem.size.width / 2)
            if (rightBorder >= leftBorder) return replacingItem
        } else {
            val leftBorder = draggingItem.offset.x + draggingItemOffsetTransformX
            val replacingItem = getItemAt(initialOffset.copy(x = leftBorder)) ?: return null
            val rightBorder = replacingItem.offset.x + (replacingItem.size.width / 2)
            if (rightBorder >= leftBorder) return replacingItem
        }
        return null
    }
}

private fun findPinnedItemByKey(
    tableState: LazyTableState,
    key: Any,
    keyOf: (Pair<Any?, Any?>?) -> Any?,
) = tableState.layoutInfo.pinnedColumnsInfo.fastFirstOrNull { keyOf(it.key as Pair<Any?, Any?>?) == key }
    ?: tableState.layoutInfo.pinnedRowsInfo.fastFirstOrNull { keyOf(it.key as Pair<Any?, Any?>?) == key }
    ?: tableState.layoutInfo.pinnedItemsInfo.fastFirstOrNull { keyOf(it.key as Pair<Any?, Any?>?) == key }
