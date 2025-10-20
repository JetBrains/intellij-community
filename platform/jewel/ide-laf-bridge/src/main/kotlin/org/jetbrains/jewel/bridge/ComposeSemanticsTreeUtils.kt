// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge

import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jewel.foundation.InternalJewelApi

@Internal
@InternalJewelApi
public object ComposeSemanticsTreeUtils {
    public fun ComposePanel.findFocusedComponent(): SemanticsNode? {
        return semanticsOwners.firstNotNullOfOrNull { o ->
            o.getAllSemanticsNodes(mergingEnabled = true).firstOrNull {
                it.config.getOrNull(SemanticsProperties.Focused) == true
            }
        }
    }

    public fun SemanticsNode.isEditableTextField(): Boolean {
        // Check if the node has editable text or supports setting text
        val editable = config.contains(SemanticsProperties.EditableText)
        val hasSetTextAction = config.contains(SemanticsActions.SetText)
        return editable || hasSetTextAction
    }

    public fun SemanticsNode.getCustomAction(label: String): CustomAccessibilityAction? =
        config.getOrNull(SemanticsActions.CustomActions)?.firstOrNull { it.label == label }
}
