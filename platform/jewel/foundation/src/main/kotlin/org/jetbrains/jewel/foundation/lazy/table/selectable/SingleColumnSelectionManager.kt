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
 * A [TableSelectionManager] that manages single column selection in a table.
 *
 * Allows only one column to be selected at a time. When a cell in any row is selected, the entire column becomes
 * selected. Selecting a different column clears the previous selection. The row key is ignored for selection checks.
 *
 * @param initialColumnKey The column key of the initially selected column, or null for no initial selection
 * @see TableSelectionManager
 * @see rememberSingleColumnSelectionManager
 */
public class SingleColumnSelectionManager(initialColumnKey: Any?) : TableSelectionManager {
    private var selectedColumnKey: Any? by mutableStateOf(initialColumnKey)

    override val selectedItems: Set<Pair<Any?, Any?>> by derivedStateOf {
        setOfNotNull(selectedColumnKey?.let { it to null })
    }

    override val interactionSource: MutableInteractionSource = MutableInteractionSource()

    override fun isSelectable(columnKey: Any?, rowKey: Any?): Boolean = true

    override fun isSelected(columnKey: Any?, rowKey: Any?): Boolean = columnKey == selectedColumnKey

    override fun handleEvent(event: SelectionEvent) {
        if (event !is TableSelectionEvent) {
            clearSelection()
            return
        }

        if (event.columnKey == null) {
            clearSelection()
            return
        }

        if (event.type == SelectionType.Multi && isSelected(event.columnKey, null)) {
            clearSelection()
            return
        }
        select(event.columnKey)
    }

    private fun select(columnKey: Any?) {
        if (selectedColumnKey == columnKey) {
            return
        }

        selectedColumnKey = columnKey
    }

    override fun clearSelection() {
        selectedColumnKey = null
    }
}
