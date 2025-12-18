// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.util

import androidx.compose.foundation.text.input.UndoState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jewel.foundation.InternalJewelApi

/** Adds custom actions for text input Undo/Redo. */
@ApiStatus.Internal
@InternalJewelApi
public fun Modifier.addUndoRedoSemantics(undoState: UndoState): Modifier = semantics {
    customActions = buildList {
        if (undoState.canUndo)
            add(
                CustomAccessibilityAction("Undo") {
                    undoState.undo()
                    true
                }
            )
        if (undoState.canRedo)
            add(
                CustomAccessibilityAction("Redo") {
                    undoState.redo()
                    true
                }
            )
    }
}
