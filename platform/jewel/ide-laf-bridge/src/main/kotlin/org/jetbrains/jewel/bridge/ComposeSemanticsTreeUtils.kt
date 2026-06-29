// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge

import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jewel.foundation.InternalJewelApi

/**
 * Internal utilities for inspecting the Compose semantics tree, such as finding the focused node or identifying text
 * fields.
 */
@Internal
@InternalJewelApi
public object ComposeSemanticsTreeUtils {
    /**
     * Returns the currently focused semantics node from this [ComposePanel], or `null` if there is none.
     *
     * This lookup fail-closes when Compose reports a transiently inconsistent semantics tree during mutation.
     */
    public fun ComposePanel.findFocusedComponent(): SemanticsNode? = findFocusedComponent { semanticsOwners }

    /**
     * Returns the currently focused semantics node from [semanticsOwners].
     *
     * The Compose semantics tree can be transiently inconsistent while it is being mutated, so this lookup fail-closes
     * on [NullPointerException] and lets callers behave as if no focused Compose component is available.
     */
    internal fun findFocusedComponent(semanticsOwners: () -> Iterable<SemanticsOwner>): SemanticsNode? =
        try {
            semanticsOwners().firstNotNullOfOrNull { o ->
                o.getAllSemanticsNodes(mergingEnabled = true).firstOrNull {
                    it.config.getOrNull(SemanticsProperties.Focused) == true
                }
            }
        } catch (_: NullPointerException) {
            null
        }

    /**
     * Returns `true` if this [SemanticsNode] represents an editable text field (has editable text or a set-text
     * action).
     */
    public fun SemanticsNode.isEditableTextField(): Boolean {
        // Check if the node has editable text or supports setting text
        val editable = config.contains(SemanticsProperties.EditableText)
        val hasSetTextAction = config.contains(SemanticsActions.SetText)
        return editable || hasSetTextAction
    }

    /**
     * Returns the [CustomAccessibilityAction] with the given [label] from this node's semantics, or `null` if not
     * found.
     */
    public fun SemanticsNode.getCustomAction(label: String): CustomAccessibilityAction? =
        config.getOrNull(SemanticsActions.CustomActions)?.firstOrNull { it.label == label }
}
