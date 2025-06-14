// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.jewel.ui.component.ContextMenuItemActionType

public interface MenuItemShortcutHintProvider {
    /**
     * Gets the formatted shortcut string for the given action identifier.
     *
     * @param contextMenuItemActionType The action type (e.g., "Copy" or "Paste").
     * @return The human-readable shortcut string (e.g., "⌘C", "Ctrl + S"), or empty if no shortcut is defined or it
     *   shouldn't be displayed.
     */
    public fun getShortcutHint(contextMenuItemActionType: ContextMenuItemActionType): String
}

public val LocalMenuItemShortcutHintProvider: ProvidableCompositionLocal<MenuItemShortcutHintProvider> =
    staticCompositionLocalOf {
        error("No LocalMenuItemShortcutHintProvider provided. Have you forgotten the theme?")
    }
