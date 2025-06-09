// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import javax.swing.KeyStroke
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction

/** Provides a way to also get the actual KeyStroke of a shortcut besides its hint */
public interface MenuItemShortcutProvider {

    /**
     * Gets the KeyStroke for a given action identifier.
     *
     * @param actionType The action type. See [ContextMenuItemOptionAction].
     * @return The Swing representation of the shortcut, or empty if a given action can't be mapped to a KeyStroke.
     */
    public fun getShortcutKeyStroke(actionType: ContextMenuItemOptionAction): KeyStroke?
}

public val LocalMenuItemShortcutProvider: ProvidableCompositionLocal<MenuItemShortcutProvider> =
    staticCompositionLocalOf {
        error("No LocalMenuItemShortcutProvider provided. Have you forgotten the theme?")
    }
