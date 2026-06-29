// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.InputMode
import javax.swing.KeyStroke
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi

/**
 * Controls the lifecycle and hierarchy of a menu, including open/close behaviour, hover state, and keyboard shortcut
 * dispatch.
 */
public interface MenuController {
    /** Callback invoked when the menu is requested to be dismissed for a given [InputMode]. */
    public val onDismissRequest: (InputMode) -> Boolean

    /** Called when the hovered state of the menu changes. */
    public fun onHoveredChange(hovered: Boolean)

    /** Closes this menu and all ancestor menus in the hierarchy. */
    public fun closeAll(mode: InputMode, force: Boolean)

    /** Closes this menu and returns whether the dismissal was accepted. */
    public fun close(mode: InputMode): Boolean

    /** Returns `true` if this menu has no parent, i.e. it is the root of the menu hierarchy. */
    public fun isRootMenu(): Boolean

    /** Returns `true` if this menu is nested inside another menu. */
    public fun isSubmenu(): Boolean

    /**
     * Creates a child [MenuController] for a submenu, linking it to this controller as its parent.
     *
     * @param onDismissRequest callback invoked when the submenu is requested to be dismissed.
     */
    public fun submenuController(onDismissRequest: (InputMode) -> Boolean): MenuController

    /**
     * Registers a keyboard shortcut action for this menu level.
     *
     * @param keyStroke the key stroke that triggers the action.
     * @param action the action to invoke when the key stroke is detected.
     */
    public fun registerShortcutAction(keyStroke: KeyStroke, action: () -> Unit)

    /** Removes all registered shortcut actions from this menu level. */
    public fun clearShortcutActions()

    /**
     * Searches for a registered shortcut matching [keyStroke] and executes it if found.
     *
     * @return `true` if a matching shortcut was found and executed, or `null` if no match was found.
     */
    public fun findAndExecuteShortcut(keyStroke: KeyStroke?): Boolean?
}

/** Default [MenuController] implementation that supports nested submenus via an optional [parentMenuController]. */
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

    /** Closes this menu by invoking [onDismissRequest] and returns whether the dismissal was accepted. */
    override fun close(mode: InputMode): Boolean = onDismissRequest(mode)

    /** Returns `true` if this menu has no parent, i.e. it is the root of the menu hierarchy. */
    override fun isRootMenu(): Boolean = parentMenuController == null

    /** Returns `true` if this menu is nested inside another menu. */
    override fun isSubmenu(): Boolean = parentMenuController != null

    /**
     * Creates a child [DefaultMenuController] for a submenu, linking it to this controller as its parent.
     *
     * @param onDismissRequest callback invoked when the submenu is requested to be dismissed.
     */
    override fun submenuController(onDismissRequest: (InputMode) -> Boolean): DefaultMenuController =
        DefaultMenuController(onDismissRequest = onDismissRequest, parentMenuController = this)

    /**
     * Registers a keyboard shortcut action for this menu level.
     *
     * @param keyStroke the key stroke that triggers the action.
     * @param action the action to invoke when the key stroke is detected.
     */
    override fun registerShortcutAction(keyStroke: KeyStroke, action: () -> Unit) {
        currentMenuShortcutActions.add(MenuShortcutAction(keyStroke, action))
    }

    /** Removes all registered shortcut actions from this menu level. */
    override fun clearShortcutActions() {
        currentMenuShortcutActions.clear()
    }

    /**
     * Searches for a registered shortcut matching [keyStroke] and executes it if found.
     *
     * @return `true` if a matching shortcut was found and executed, or `null` if no match was found.
     */
    override fun findAndExecuteShortcut(keyStroke: KeyStroke?): Boolean? {
        val actionToExecute = currentMenuShortcutActions.firstOrNull { it.keyStroke == keyStroke }
        if (actionToExecute != null) {
            actionToExecute.action.invoke()
            return true
        }
        return null
    }
}

/** CompositionLocal that provides the nearest [MenuController] in the composition. */
public val LocalMenuController: ProvidableCompositionLocal<MenuController> = staticCompositionLocalOf {
    error("No MenuController provided. Have you forgotten the theme?")
}

internal data class MenuShortcutAction(val keyStroke: KeyStroke, val action: () -> Unit)
