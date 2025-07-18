// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.InputMode
import javax.swing.KeyStroke
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi

public interface MenuController {
    public val onDismissRequest: (InputMode) -> Boolean

    public fun onHoveredChange(hovered: Boolean)

    public fun closeAll(mode: InputMode, force: Boolean)

    public fun close(mode: InputMode): Boolean

    public fun isRootMenu(): Boolean

    public fun isSubmenu(): Boolean

    public fun submenuController(onDismissRequest: (InputMode) -> Boolean): MenuController

    public fun registerShortcutAction(keyStroke: KeyStroke, action: () -> Unit)

    public fun clearShortcutActions()

    public fun findAndExecuteShortcut(keyStroke: KeyStroke?): Boolean?
}

@ApiStatus.Internal
@InternalJewelApi
public class DefaultMenuController(
    override val onDismissRequest: (InputMode) -> Boolean,
    private val parentMenuController: DefaultMenuController? = null,
) : MenuController {
    private var isHovered: Boolean = false
    private val currentMenuShortcutActions = mutableListOf<MenuShortcutAction>()

    /**
     * Called when the hovered state of the menu changes. This is used to abort parent menu closing in unforced mode
     * when submenu closed by click parent menu's item.
     *
     * @param hovered true if the menu is hovered, false otherwise.
     */
    override fun onHoveredChange(hovered: Boolean) {
        isHovered = hovered
    }

    /**
     * Close all menus in the hierarchy.
     *
     * @param mode the input mode, menus close by pointer or keyboard event.
     * @param force true to force close all menus ignore parent hover state, false otherwise.
     */
    override fun closeAll(mode: InputMode, force: Boolean) {
        // We ignore the pointer event if the menu is hovered in unforced mode.
        if (!force && mode == InputMode.Touch && isHovered) return

        if (onDismissRequest(mode)) {
            parentMenuController?.closeAll(mode, force)
        }
    }

    override fun close(mode: InputMode): Boolean = onDismissRequest(mode)

    override fun isRootMenu(): Boolean = parentMenuController == null

    override fun isSubmenu(): Boolean = parentMenuController != null

    override fun submenuController(onDismissRequest: (InputMode) -> Boolean): DefaultMenuController =
        DefaultMenuController(onDismissRequest = onDismissRequest, parentMenuController = this)

    override fun registerShortcutAction(keyStroke: KeyStroke, action: () -> Unit) {
        currentMenuShortcutActions.add(MenuShortcutAction(keyStroke, action))
    }

    override fun clearShortcutActions() {
        currentMenuShortcutActions.clear()
    }

    override fun findAndExecuteShortcut(keyStroke: KeyStroke?): Boolean? {
        val actionToExecute = currentMenuShortcutActions.firstOrNull { it.keyStroke == keyStroke }
        if (actionToExecute != null) {
            actionToExecute.action.invoke()
            return true
        }
        return null
    }
}

public val LocalMenuController: ProvidableCompositionLocal<MenuController> = staticCompositionLocalOf {
    error("No MenuController provided. Have you forgotten the theme?")
}

internal data class MenuShortcutAction(val keyStroke: KeyStroke, val action: () -> Unit)
