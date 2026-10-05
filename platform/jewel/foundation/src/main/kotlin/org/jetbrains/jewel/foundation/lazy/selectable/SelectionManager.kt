// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.selectable

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.modifier.modifierLocalProvider

/**
 * Manages the selection state of a selectable lazy layout.
 *
 * A [SelectionManager] is responsible for deciding which items can be selected, tracking the
 * currently selected items, and reacting to [SelectionEvent]s dispatched by the UI. Different
 * implementations can provide different selection strategies (e.g. single-item, single-row,
 * multi-select).
 *
 * Attach a manager to a layout node with [selectionManagerProvider], and read it in descendant nodes with
 * [selectionManagerConsumer].
 */
public interface SelectionManager {
    /** Interaction source used to drive focus and hover state for the managed layout. */
    public val interactionSource: MutableInteractionSource

    /** The set of **keys** that are currently selected. */
    public val selectedItems: Set<Any>

    /**
     * Returns `true` if the item identified by [itemKey] is eligible for selection.
     *
     * Implementations may use this to mark certain items as non-interactive (e.g. header rows or
     * disabled cells).
     */
    public fun isSelectable(itemKey: Any?): Boolean

    /** Returns `true` if the item identified by [itemKey] is currently selected. */
    public fun isSelected(itemKey: Any?): Boolean

    /**
     * Processes a [SelectionEvent] and updates the selection state accordingly.
     *
     * The exact behaviour depends on the implementation — for example, a single-item manager will
     * replace the current selection, while a multi-select manager may extend or toggle it.
     */
    public fun handleEvent(event: SelectionEvent)

    /** Removes all items from the current selection. */
    public fun clearSelection()
}

internal val ModifierLocalSelectionManager = modifierLocalOf<SelectionManager?> { null }

/**
 * Attaches [manager] to this node so that descendant nodes can retrieve it via
 * [selectionManagerConsumer].
 *
 * Also makes the node focusable and creates a focus group, which is required for keyboard
 * navigation within the selectable layout.
 */
public fun Modifier.selectionManagerProvider(manager: SelectionManager): Modifier =
    focusable(interactionSource = manager.interactionSource).focusGroup().modifierLocalProvider(
        ModifierLocalSelectionManager
    ) {
        manager
    }

/**
 * Reads the nearest [SelectionManager] provided by an ancestor [selectionManagerProvider] node and applies
 * the [Modifier] returned by [factory].
 *
 * [factory] is only invoked when a manager is present; if no manager is found the modifier is a
 * no-op.
 */
@Suppress("ModifierComposed") // To fix in JEWEL-921
public fun Modifier.selectionManagerConsumer(factory: @Composable (SelectionManager) -> Modifier): Modifier = composed {
    var manager by remember { mutableStateOf<SelectionManager?>(null) }

    this.modifierLocalConsumer { manager = ModifierLocalSelectionManager.current }
        .then(manager?.let { factory(it) } ?: Modifier)
}

internal fun PointerKeyboardModifiers.selectionType(): SelectionType =
    when {
        this.isCtrlPressed || this.isMetaPressed -> SelectionType.Multi
        this.isShiftPressed -> SelectionType.Contiguous
        else -> SelectionType.Normal
    }
