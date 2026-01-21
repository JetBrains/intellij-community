// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table.draggable

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.Modifier
import androidx.compose.ui.modifier.ModifierLocal
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.modifier.modifierLocalProvider
import org.jetbrains.jewel.foundation.lazy.draggable.LazyLayoutDraggingState
import org.jetbrains.jewel.foundation.lazy.draggable.draggableLayout
import org.jetbrains.jewel.foundation.lazy.draggable.draggingGestures
import org.jetbrains.jewel.foundation.lazy.draggable.draggingOffset

internal val ModifierLocalLazyTableRowDraggingState = modifierLocalOf<LazyTableRowDraggingState?> { null }

internal val ModifierLocalLazyTableColumnDraggingState = modifierLocalOf<LazyTableColumnDraggingState?> { null }

/**
 * Applies drag-and-drop behavior to a LazyTable by providing row and/or column dragging states.
 *
 * This modifier should be applied to the LazyTable itself to enable drag-and-drop reordering. It provides the dragging
 * state context that child cells can access via [lazyTableDraggableRowCell], [lazyTableDraggableColumnCell], and
 * [lazyTableCellDraggingOffset] modifiers.
 *
 * @param rowDraggingState Optional state managing row drag-and-drop operations. When provided, enables row reordering
 * @param columnDraggingState Optional state managing column drag-and-drop operations. When provided, enables column
 *   reordering
 */
public fun Modifier.lazyTableDraggable(
    rowDraggingState: LazyTableRowDraggingState? = null,
    columnDraggingState: LazyTableColumnDraggingState? = null,
): Modifier =
    draggableLayout()
        .modifierLocalProvider(ModifierLocalLazyTableRowDraggingState) { rowDraggingState }
        .modifierLocalProvider(ModifierLocalLazyTableColumnDraggingState) { columnDraggingState }

/**
 * Applies dragging offset to a table cell based on active row and/or column drag operations.
 *
 * This is a convenience overload that accepts a [Pair] of column and row keys. Use this modifier on non-pinned cells to
 * make them visually follow when their row or column is being dragged.
 *
 * @param key A pair where the first element is the column key and the second element is the row key
 */
public fun Modifier.lazyTableCellDraggingOffset(key: Pair<Any?, Any?>): Modifier =
    lazyTableCellDraggingOffset(key.first, key.second)

/**
 * Applies dragging offset to a table cell based on active row and/or column drag operations.
 *
 * Use this modifier on non-pinned cells to make them visually follow when their row or column is being dragged. The
 * cell will automatically offset vertically when its row is dragged and horizontally when its column is dragged.
 *
 * @param columnKey The key identifying the cell's column, or null if not applicable
 * @param rowKey The key identifying the cell's row, or null if not applicable
 */
public fun Modifier.lazyTableCellDraggingOffset(columnKey: Any?, rowKey: Any?): Modifier =
    draggingOffset(
            ModifierLocalLazyTableRowDraggingState as ModifierLocal<LazyLayoutDraggingState<*>>,
            rowKey,
            Orientation.Vertical,
        )
        .draggingOffset(
            ModifierLocalLazyTableColumnDraggingState as ModifierLocal<LazyLayoutDraggingState<*>>,
            columnKey,
            Orientation.Horizontal,
        )

/**
 * Makes a pinned column cell draggable for row reordering.
 *
 * This is a convenience overload that accepts a [Pair] of column and row keys and extracts the row key. Apply this
 * modifier to cells in pinned columns that should act as drag handles for reordering rows.
 *
 * @param key A pair where the first element is the column key and the second element is the row key
 */
public fun Modifier.lazyTableDraggableRowCell(key: Pair<Any?, Any?>): Modifier = lazyTableDraggableRowCell(key.second)

/**
 * Makes a pinned column cell draggable for row reordering.
 *
 * Apply this modifier to cells in pinned columns to enable row drag-and-drop reordering. The cell will detect drag
 * gestures and apply vertical offset during the drag. Requires a [LazyTableRowDraggingState] to be provided via
 * [lazyTableDraggable] on the table.
 *
 * @param key The key identifying the row to be dragged
 */
public fun Modifier.lazyTableDraggableRowCell(key: Any?): Modifier =
    draggingGestures(ModifierLocalLazyTableRowDraggingState as ModifierLocal<LazyLayoutDraggingState<*>>, key)
        .draggingOffset(
            ModifierLocalLazyTableRowDraggingState as ModifierLocal<LazyLayoutDraggingState<*>>,
            key,
            Orientation.Vertical,
        )

/**
 * Makes a pinned row cell draggable for column reordering.
 *
 * This is a convenience overload that accepts a [Pair] of column and row keys and extracts the column key. Apply this
 * modifier to cells in pinned rows that should act as drag handles for reordering columns.
 *
 * @param key A pair where the first element is the column key and the second element is the row key
 */
public fun Modifier.lazyTableDraggableColumnCell(key: Pair<Any?, Any?>): Modifier =
    lazyTableDraggableColumnCell(key.first)

/**
 * Makes a pinned row cell draggable for column reordering.
 *
 * Apply this modifier to cells in pinned rows to enable column drag-and-drop reordering. The cell will detect drag
 * gestures and apply horizontal offset during the drag. Requires a [LazyTableColumnDraggingState] to be provided via
 * [lazyTableDraggable] on the table.
 *
 * @param key The key identifying the column to be dragged
 */
public fun Modifier.lazyTableDraggableColumnCell(key: Any?): Modifier =
    draggingGestures(ModifierLocalLazyTableColumnDraggingState as ModifierLocal<LazyLayoutDraggingState<*>>, key)
        .draggingOffset(
            ModifierLocalLazyTableColumnDraggingState as ModifierLocal<LazyLayoutDraggingState<*>>,
            key,
            Orientation.Horizontal,
        )
