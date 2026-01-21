// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.lazy.layout.LazyLayoutPinnableItem
import androidx.compose.foundation.lazy.layout.getDefaultLazyLayoutKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.IntOffset

internal interface LazyTableItemProvider : LazyLayoutItemProvider {
    val rowCount: Int

    val columnCount: Int

    val keyPositionMap: LazyTableItemKeyPositionMap

    fun getContentType(position: IntOffset): Any?

    fun getIndex(position: IntOffset): Int

    fun getKey(position: IntOffset): Any

    fun LazyTableLayoutScope.getColumnConstraints(column: Int): LazyTableScope.ColumnSize?

    fun LazyTableLayoutScope.getRowConstraints(row: Int): LazyTableScope.RowSize?
}

@Composable
internal fun rememberLazyTableItemProviderLambda(
    state: LazyTableState,
    pinnedColumns: Int,
    pinnedRows: Int,
    content: LazyTableScope.() -> Unit,
): () -> LazyTableItemProvider {
    val latestContent = rememberUpdatedState(content)

    return remember(state, pinnedColumns, pinnedRows) {
        val scope = LazyTableItemScopeImpl()

        val intervalContentState =
            derivedStateOf(referentialEqualityPolicy()) { LazyTableIntervalContent(latestContent.value, state) }

        val itemProvider =
            derivedStateOf(referentialEqualityPolicy()) {
                val intervalContent = intervalContentState.value

                val map =
                    NearestRangeKeyPositionMap(
                        rowRange = state.nearestRowRange,
                        columnRange = state.nearestColumnRange,
                        pinnedColumns = pinnedColumns,
                        pinnedRows = pinnedRows,
                        content = intervalContent,
                    )
                // Register the new map and dispose the previous one to return HashMaps to pool
                LazyTableItemProviderImpl(
                    state = state,
                    content = intervalContent,
                    itemScope = scope,
                    keyPositionMap = map,
                )
            }
        itemProvider::value
    }
}

internal class LazyTableItemProviderImpl(
    private val state: LazyTableState,
    private val content: LazyTableContent?,
    private val itemScope: LazyTableItemScope,
    override val keyPositionMap: LazyTableItemKeyPositionMap,
) : LazyTableItemProvider {
    override val rowCount: Int
        get() = content?.rowCount ?: 0

    override val columnCount: Int
        get() = content?.columnCount ?: 0

    override val itemCount: Int
        get() = rowCount * columnCount

    @Composable
    override fun Item(index: Int, key: Any) {
        if (index < 0) return
        content ?: return

        LazyLayoutPinnableItem(key, index, state.pinnedItems) { content.Item(itemScope, index) }
    }

    override fun getContentType(index: Int): Any? {
        val coordinate = content?.getPosition(index) ?: return null
        return content.getContentType(coordinate)
    }

    override fun getContentType(position: IntOffset): Any? = content?.getContentType(position)

    override fun getIndex(key: Any): Int {
        val position = keyPositionMap.getPosition(key) ?: return -1
        return content?.getIndex(position) ?: -1
    }

    override fun getIndex(position: IntOffset): Int = content?.getIndex(position) ?: -1

    override fun getKey(index: Int): Any {
        val coordinate = content?.getPosition(index) ?: return getDefaultLazyLayoutKey(index)
        return content.getKey(coordinate)
    }

    override fun getKey(position: IntOffset): Any =
        keyPositionMap.getKey(position) ?: content?.getKey(position) ?: getDefaultLazyLayoutKey(getIndex(position))

    override fun LazyTableLayoutScope.getColumnConstraints(column: Int): LazyTableScope.ColumnSize? {
        content ?: return null
        return with(content) { this@getColumnConstraints.getColumnConstraints(column) }
    }

    override fun LazyTableLayoutScope.getRowConstraints(row: Int): LazyTableScope.RowSize? {
        content ?: return null
        return with(content) { this@getRowConstraints.getRowConstraints(row) }
    }
}
