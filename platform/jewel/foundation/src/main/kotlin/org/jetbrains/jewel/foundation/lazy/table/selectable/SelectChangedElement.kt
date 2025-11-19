// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table.selectable

import androidx.compose.ui.Modifier
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.observeReads
import org.jetbrains.jewel.foundation.lazy.selectable.ModifierLocalSelectionManager

/**
 * Observes selection state changes for a table cell and invokes a callback when the selection state changes.
 *
 * This is a convenience overload that accepts a [Pair] of column and row keys. Use this modifier to receive
 * notifications when a cell's selection state changes, allowing you to update visual styling or trigger other actions.
 *
 * The callback is invoked with `true` when the cell becomes selected and `false` when it becomes unselected. This
 * modifier automatically tracks the selection state from the [TableSelectionManager] and only invokes the callback when
 * the state actually changes.
 *
 * @param key A pair where the first element is the column key and the second element is the row key
 * @param onSelectChanged Callback invoked with the new selection state (true for selected, false for unselected)
 * @see selectableCell
 * @see TableSelectionManager
 */
public fun Modifier.onSelectChanged(key: Pair<Any?, Any?>, onSelectChanged: (Boolean) -> Unit): Modifier =
    onSelectChanged(key.first, key.second, onSelectChanged)

/**
 * Observes selection state changes for a table cell and invokes a callback when the selection state changes.
 *
 * Use this modifier to receive notifications when a cell's selection state changes, allowing you to update visual
 * styling or trigger other actions. The callback is invoked with `true` when the cell becomes selected and `false` when
 * it becomes unselected.
 *
 * This modifier automatically tracks the selection state from the [TableSelectionManager] and only invokes the callback
 * when the state actually changes, avoiding unnecessary recompositions.
 *
 * @param columnKey The key identifying the cell's column, or null if not applicable
 * @param rowKey The key identifying the cell's row, or null if not applicable
 * @param onSelectChanged Callback invoked with the new selection state (true for selected, false for unselected)
 * @see selectableCell
 * @see TableSelectionManager
 */
public fun Modifier.onSelectChanged(columnKey: Any?, rowKey: Any?, onSelectChanged: (Boolean) -> Unit): Modifier =
    then(SelectChangedElement(columnKey, rowKey, onSelectChanged))

internal class SelectChangedElement(
    private var columnKey: Any?,
    private var rowKey: Any?,
    private var onSelectChanged: (Boolean) -> Unit,
) : ModifierNodeElement<SelectChangedNode>() {
    override fun create(): SelectChangedNode = SelectChangedNode(columnKey, rowKey, onSelectChanged)

    override fun update(node: SelectChangedNode) {
        node.update(columnKey, rowKey, onSelectChanged)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectChangedElement) return false

        if (columnKey != other.columnKey) return false
        if (rowKey != other.rowKey) return false
        if (onSelectChanged != other.onSelectChanged) return false

        return true
    }

    override fun hashCode(): Int {
        var result = columnKey?.hashCode() ?: 0
        result = 31 * result + (rowKey?.hashCode() ?: 0)
        result = 31 * result + onSelectChanged.hashCode()
        return result
    }
}

internal class SelectChangedNode(
    private var columnKey: Any?,
    private var rowKey: Any?,
    private var onSelectChanged: (Boolean) -> Unit,
) : Modifier.Node(), ModifierLocalModifierNode, ObserverModifierNode {
    var isSelected: Boolean = false

    fun update(columnKey: Any?, rowKey: Any?, onSelectChanged: (Boolean) -> Unit) {
        this.columnKey = columnKey
        this.rowKey = rowKey
        this.onSelectChanged = onSelectChanged
        this.isSelected = false
        onObservedReadsChanged()
    }

    override fun onAttach() {
        onObservedReadsChanged()
    }

    override fun onObservedReadsChanged() {
        observeReads {
            val manager = ModifierLocalSelectionManager.current as? TableSelectionManager ?: return@observeReads
            val newValue = manager.isSelected(columnKey, rowKey)
            if (newValue != isSelected) {
                isSelected = newValue
                onSelectChanged(newValue)
            }
        }
    }
}
