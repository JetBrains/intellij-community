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
 * A [TableSelectionManager] that manages single row selection in a table.
 *
 * Allows only one row to be selected at a time. When a cell in any column is selected, the entire row becomes selected.
 * Selecting a different row clears the previous selection. The column key is ignored for selection checks.
 *
 * @param initialRowKey The row key of the initially selected row, or null for no initial selection
 * @see TableSelectionManager
 * @see rememberSingleRowSelectionManager
 */
public class SingleRowSelectionManager(initialRowKey: Any?) : TableSelectionManager {
    private var selectedRowKey: Any? by mutableStateOf(initialRowKey)

    override val interactionSource: MutableInteractionSource = MutableInteractionSource()

    override val selectedItems: Set<Pair<Any?, Any?>> by derivedStateOf {
        setOfNotNull(selectedRowKey?.let { null to it })
    }

    override fun isSelectable(columnKey: Any?, rowKey: Any?): Boolean = true

    override fun isSelected(columnKey: Any?, rowKey: Any?): Boolean = rowKey == selectedRowKey

    override fun handleEvent(event: SelectionEvent) {
        if (event !is TableSelectionEvent) {
            clearSelection()
            return
        }

        if (event.rowKey == null) {
            clearSelection()
            return
        }

        if (event.type == SelectionType.Multi && isSelected(null, event.rowKey)) {
            clearSelection()
            return
        }
        select(event.rowKey)
    }

    private fun select(rowKey: Any?) {
        if (selectedRowKey == rowKey) {
            return
        }

        selectedRowKey = rowKey
    }

    override fun clearSelection() {
        selectedRowKey = null
    }
}
