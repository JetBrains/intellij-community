// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table.selectable

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.jewel.foundation.lazy.selectable.SelectionEvent
import org.jetbrains.jewel.foundation.lazy.selectable.SelectionType

/**
 * A [TableSelectionManager] that manages single cell selection in a table.
 *
 * Allows only one cell (identified by both column and row keys) to be selected at a time. When a new cell is selected,
 * the previous selection is cleared. Both column and row keys must match for a cell to be considered selected.
 *
 * @param initialColumnKey The column key of the initially selected cell, or null for no initial selection
 * @param initialRowKey The row key of the initially selected cell, or null for no initial selection
 * @see TableSelectionManager
 * @see rememberSingleCellSelectionManager
 */
public open class SingleCellSelectionManager(initialColumnKey: Any?, initialRowKey: Any?) : TableSelectionManager {
    private var selectedKey: Pair<Any?, Any?>? by
        mutableStateOf(
            if (initialRowKey == null || initialColumnKey == null) null else (initialColumnKey to initialRowKey)
        )

    override val interactionSource: MutableInteractionSource = MutableInteractionSource()

    override val selectedItems: Set<Pair<Any?, Any?>> by derivedStateOf { setOfNotNull(selectedKey) }

    override fun isSelectable(columnKey: Any?, rowKey: Any?): Boolean = true

    override fun isSelected(columnKey: Any?, rowKey: Any?): Boolean =
        selectedKey?.first == columnKey && selectedKey?.second == rowKey

    override fun handleEvent(event: SelectionEvent) {
        if (event !is TableSelectionEvent) {
            clearSelection()
            return
        }

        if (event.rowKey == null || event.columnKey == null) {
            clearSelection()
            return
        }

        if (event.type == SelectionType.Multi && isSelected(event.columnKey, event.rowKey)) {
            clearSelection()
            return
        }
        select(event.columnKey, event.rowKey)
    }

    private fun select(columnKey: Any?, rowKey: Any?) {
        if (isSelected(columnKey, rowKey)) {
            return
        }

        selectedKey = if (columnKey == null || rowKey == null) null else (columnKey to rowKey)
    }

    override fun clearSelection() {
        if (selectedKey == null) {
            return
        }

        selectedKey = null
    }
}
