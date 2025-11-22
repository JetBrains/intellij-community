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

public interface SelectionManager {
    public val interactionSource: MutableInteractionSource

    public val selectedItems: Set<Any>

    public fun isSelectable(itemKey: Any?): Boolean

    public fun isSelected(itemKey: Any?): Boolean

    public fun handleEvent(event: SelectionEvent)

    public fun clearSelection()
}

internal val ModifierLocalSelectionManager = modifierLocalOf<SelectionManager?> { null }

public fun Modifier.selectionManager(manager: SelectionManager): Modifier =
    focusable(interactionSource = manager.interactionSource).focusGroup().modifierLocalProvider(
        ModifierLocalSelectionManager
    ) {
        manager
    }

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
