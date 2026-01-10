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

public abstract class LazyTableDraggableState(
    public val tableState: LazyTableState,
    public val itemCanMove: (Any?) -> Boolean,
    public val onMove: (Any?, Any?) -> Boolean,
) : LazyLayoutDraggingState<LazyTableItemInfo>() {
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

public class LazyTableRowDraggingState(
    tableState: LazyTableState,
    itemCanMove: (Any?) -> Boolean,
    onMove: (Any?, Any?) -> Boolean,
) : LazyTableDraggableState(tableState, itemCanMove, onMove) {
    override val LazyTableItemInfo.index: Int
        get() = this.row

    override val LazyTableItemInfo.key: Any?
        get() = (this.key as Pair<Any?, Any?>?)?.second

    override fun getItemWithKey(key: Any): LazyTableItemInfo? =
        tableState.layoutInfo.pinnedColumnsInfo.fastFirstOrNull { (it.key as Pair<Any?, Any?>?)?.second == key }
            ?: tableState.layoutInfo.pinnedRowsInfo.fastFirstOrNull { (it.key as Pair<Any?, Any?>?)?.second == key }
            ?: tableState.layoutInfo.pinnedItemsInfo.fastFirstOrNull { (it.key as Pair<Any?, Any?>?)?.second == key }

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

public class LazyTableColumnDraggingState(
    tableState: LazyTableState,
    itemCanMove: (Any?) -> Boolean,
    onMove: (Any?, Any?) -> Boolean,
) : LazyTableDraggableState(tableState, itemCanMove, onMove) {
    override val LazyTableItemInfo.index: Int
        get() = this.column

    override val LazyTableItemInfo.key: Any?
        get() = (this.key as Pair<Any?, Any?>?)?.first

    override fun getItemWithKey(key: Any): LazyTableItemInfo? =
        tableState.layoutInfo.pinnedColumnsInfo.fastFirstOrNull { (it.key as Pair<Any?, Any?>?)?.first == key }
            ?: tableState.layoutInfo.pinnedRowsInfo.fastFirstOrNull { (it.key as Pair<Any?, Any?>?)?.first == key }
            ?: tableState.layoutInfo.pinnedItemsInfo.fastFirstOrNull { (it.key as Pair<Any?, Any?>?)?.first == key }

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
