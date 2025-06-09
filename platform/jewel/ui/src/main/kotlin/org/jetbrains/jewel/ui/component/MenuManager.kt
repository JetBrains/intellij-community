package org.jetbrains.jewel.ui.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.InputMode
import javax.swing.KeyStroke
import org.jetbrains.jewel.foundation.InternalJewelApi

@Deprecated(
    message =
        "The MenuManager class has been superseded by the MenuController interface, " +
            "which offers improved abstraction and new functionalities like shortcut management. " +
            "Depend on the DefaultMenuController class for all menu operations.",
    replaceWith = ReplaceWith("DefaultMenuController"),
)
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
