// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.menu

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * State holder for tracking which menu item is currently highlighted (either by hover or keyboard navigation).
 *
 * This state is menu-scoped, meaning each menu (including submenus) has its own instance. Only one item can be
 * highlighted at a time within a menu.
 */
@Stable
public class MenuHighlightState {
    /** The index of the currently highlighted menu item, or null if no item is highlighted. */
    public var highlightedItemIndex: Int? by mutableStateOf(null)
        private set

    /**
     * Highlights the menu item at the specified index.
     *
     * @param index The index of the item to highlight, or null to clear the highlight
     */
    public fun highlightItem(index: Int?) {
        highlightedItemIndex = index
    }

    /** Clears the current highlight, setting [highlightedItemIndex] to null. */
    public fun clearHighlight() {
        highlightedItemIndex = null
    }
}

/**
 * CompositionLocal for providing the menu item index to each item in the menu.
 *
 * This allows menu items to know their position without parameter drilling through the composable hierarchy. The value
 * is null for non-selectable items (separators, passive items).
 */
internal val LocalMenuItemIndex: ProvidableCompositionLocal<Int?> = staticCompositionLocalOf { null }

/**
 * CompositionLocal for providing the shared [MenuHighlightState] to all items within a menu.
 *
 * This is menu-scoped - each menu (including submenus) provides its own instance to its items. The value is null
 * outside of a menu context.
 */
public val LocalMenuHighlightState: ProvidableCompositionLocal<MenuHighlightState?> = staticCompositionLocalOf { null }
