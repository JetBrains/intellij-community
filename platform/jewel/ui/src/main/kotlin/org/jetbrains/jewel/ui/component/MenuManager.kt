package org.jetbrains.jewel.ui.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.InputMode

public class MenuManager(
    public val onDismissRequest: (InputMode) -> Boolean,
    private val parentMenuManager: MenuManager? = null,
) {
    private var isHovered: Boolean = false

    /**
     * Called when the hovered state of the menu changes. This is used to abort parent menu closing in unforced mode
     * when submenu closed by click parent menu's item.
     *
     * @param hovered true if the menu is hovered, false otherwise.
     */
    internal fun onHoveredChange(hovered: Boolean) {
        isHovered = hovered
    }

    /**
     * Close all menus in the hierarchy.
     *
     * @param mode the input mode, menus close by pointer or keyboard event.
     * @param force true to force close all menus ignore parent hover state, false otherwise.
     */
    public fun closeAll(mode: InputMode, force: Boolean) {
        // We ignore the pointer event if the menu is hovered in unforced mode.
        if (!force && mode == InputMode.Touch && isHovered) return

        if (onDismissRequest(mode)) {
            parentMenuManager?.closeAll(mode, force)
        }
    }

    public fun close(mode: InputMode): Boolean = onDismissRequest(mode)

    public fun isRootMenu(): Boolean = parentMenuManager == null

    public fun isSubmenu(): Boolean = parentMenuManager != null

    public fun submenuManager(onDismissRequest: (InputMode) -> Boolean): MenuManager =
        MenuManager(onDismissRequest = onDismissRequest, parentMenuManager = this)
}

public val LocalMenuManager: ProvidableCompositionLocal<MenuManager> = staticCompositionLocalOf {
    error("No MenuManager provided. Have you forgotten the theme?")
}
