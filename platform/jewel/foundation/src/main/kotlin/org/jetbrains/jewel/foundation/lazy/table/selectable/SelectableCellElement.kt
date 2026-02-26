// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table.selectable

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import org.jetbrains.jewel.foundation.lazy.selectable.ModifierLocalSelectionManager
import org.jetbrains.jewel.foundation.lazy.selectable.selectionType

/**
 * Makes a table cell participate in selection interactions.
 *
 * Apply this modifier to table cells to enable selection behavior. The cell will respond to pointer events (clicks) and
 * communicate with the [TableSelectionManager] provided via [selectionManager] on the table. The [selectionUnit]
 * determines what gets selected when this cell is clicked (the cell itself, the entire row, or the entire column).
 *
 * Use in combination with [onSelectChanged] to receive selection state updates and apply visual feedback.
 *
 * @param columnKey The key identifying the cell's column, or null if not applicable
 * @param rowKey The key identifying the cell's row, or null if not applicable
 * @param selectionUnit The unit of selection for this cell. Controls whether clicking selects the cell, row, or column
 * @see onSelectChanged
 * @see TableSelectionManager
 */
public fun Modifier.selectableCell(
    columnKey: Any?,
    rowKey: Any?,
    selectionUnit: TableSelectionUnit = TableSelectionUnit.Cell,
): Modifier = then(SelectableCellElement(columnKey, rowKey, selectionUnit))

/**
 * Makes a table cell participate in selection interactions.
 *
 * This is a convenience overload that accepts a [Pair] of column and row keys. Apply this modifier to table cells to
 * enable selection behavior.
 *
 * @param key A pair where the first element is the column key and the second element is the row key
 * @param selectionUnit The unit of selection for this cell. Controls whether clicking selects the cell, row, or column
 * @see onSelectChanged
 * @see TableSelectionManager
 */
public fun Modifier.selectableCell(
    key: Pair<Any?, Any?>,
    selectionUnit: TableSelectionUnit = TableSelectionUnit.Cell,
): Modifier = selectableCell(key.first, key.second, selectionUnit)

private class SelectableCellElement(
    private val columnKey: Any?,
    private val rowKey: Any?,
    private val selectionUnit: TableSelectionUnit,
) : ModifierNodeElement<SelectableCellNode>() {
    override fun create(): SelectableCellNode = SelectableCellNode(columnKey, rowKey, selectionUnit)

    override fun update(node: SelectableCellNode) {
        node.update(columnKey, rowKey, selectionUnit)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "SelectableCell"
        properties["columnKey"] = columnKey
        properties["rowKey"] = rowKey
        properties["selectionUnit"] = selectionUnit
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectableCellElement) return false

        if (columnKey != other.columnKey) return false
        if (rowKey != other.rowKey) return false
        if (selectionUnit != other.selectionUnit) return false

        return true
    }

    override fun hashCode(): Int {
        var result = columnKey?.hashCode() ?: 0
        result = 31 * result + (rowKey?.hashCode() ?: 0)
        result = 31 * result + selectionUnit.hashCode()
        return result
    }
}

internal class SelectableCellNode(
    private var columnKey: Any?,
    private var rowKey: Any?,
    private var selectionUnit: TableSelectionUnit,
) : DelegatingNode(), ModifierLocalModifierNode {
    private var pointerInputNode: SelectableCellPointerInputNode? = null

    fun update(columnKey: Any?, rowKey: Any?, selectionUnit: TableSelectionUnit) {
        this.columnKey = columnKey
        this.rowKey = rowKey
        this.selectionUnit = selectionUnit
        pointerInputNode?.update(columnKey, rowKey, selectionUnit)
    }

    override fun onAttach() {
        val selectionManager = ModifierLocalSelectionManager.current as? TableSelectionManager ?: return

        if (pointerInputNode == null) {
            pointerInputNode = SelectableCellPointerInputNode(columnKey, rowKey, selectionUnit)
        }

        val pointerInputNode = pointerInputNode ?: return

        pointerInputNode.selectionManager = selectionManager

        if (!pointerInputNode.isAttached) {
            delegate(pointerInputNode)
        }
    }
}

private class SelectableCellPointerInputNode(
    private var columnKey: Any?,
    private var rowKey: Any?,
    private var selectionUnit: TableSelectionUnit,
) : Modifier.Node(), PointerInputModifierNode {
    var selectionManager: TableSelectionManager? = null

    fun update(columnKey: Any?, rowKey: Any?, selectionUnit: TableSelectionUnit) {
        this.columnKey = columnKey
        this.rowKey = rowKey
        this.selectionUnit = selectionUnit
    }

    override fun onCancelPointerInput() = Unit

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        val manager = selectionManager ?: return

        when (pointerEvent.type) {
            PointerEventType.Press -> {
                manager.handleEvent(
                    TableSelectionEvent(
                        columnKey,
                        rowKey,
                        selectionUnit,
                        pointerEvent.keyboardModifiers.selectionType(),
                    )
                )
            }
        }
    }
}
