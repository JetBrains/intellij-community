package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberPopupPositionProviderAtPosition
import java.awt.event.InputEvent
import javax.swing.KeyStroke
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.theme.menuStyle

/** Jewel's implementation of [ContextMenuRepresentation] showing standard Swing-like context menu items. */
public object ContextMenuRepresentation : ContextMenuRepresentation {
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val status = state.status
        val currentItems by rememberUpdatedState(items)

        if (status is ContextMenuState.Status.Open) {
            val resolvedItems by remember { derivedStateOf { currentItems() } }

            if (resolvedItems.isEmpty()) {
                // If there is no entry in the context menu, close it immediately.
                state.status = ContextMenuState.Status.Closed
                return
            }

            ContextMenu(
                position = status.rect.center,
                onDismissRequest = {
                    state.status = ContextMenuState.Status.Closed
                    true
                },
                style = JewelTheme.menuStyle,
            ) {
                contextItems(resolvedItems)
            }
        }
    }
}

@Composable
internal fun ContextMenu(
    position: Offset,
    onDismissRequest: (InputMode) -> Boolean,
    modifier: Modifier = Modifier,
    focusable: Boolean = true,
    style: MenuStyle = JewelTheme.menuStyle,
    content: MenuScope.() -> Unit,
) {
    var focusManager: FocusManager? by remember { mutableStateOf(null) }
    var inputModeManager: InputModeManager? by remember { mutableStateOf(null) }
    val menuController = remember(onDismissRequest) { DefaultMenuController(onDismissRequest = onDismissRequest) }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)

    Popup(
        popupPositionProvider = rememberPopupPositionProviderAtPosition(position, style.metrics.offset),
        onDismissRequest = { currentOnDismissRequest(InputMode.Touch) },
        properties = PopupProperties(focusable = focusable),
        onPreviewKeyEvent = { false },
        onKeyEvent = {
            val currentFocusManager = checkNotNull(focusManager) { "FocusManager must not be null" }
            val currentInputModeManager = checkNotNull(inputModeManager) { "InputModeManager must not be null" }
            val swingKeyStroke = composeKeyEventToSwingKeyStroke(it)

            menuController.findAndExecuteShortcut(swingKeyStroke)
                ?: handlePopupMenuOnKeyEvent(it, currentFocusManager, currentInputModeManager, menuController)
        },
        cornerSize = style.metrics.cornerSize,
    ) {
        @Suppress("AssignedValueIsNeverRead")
        focusManager = LocalFocusManager.current
        @Suppress("AssignedValueIsNeverRead")
        inputModeManager = LocalInputModeManager.current

        CompositionLocalProvider(LocalMenuController provides menuController) {
            MenuContent(modifier = modifier, content = content)
        }
    }
}

private fun MenuScope.contextItems(items: List<ContextMenuItem>) {
    for (item in items) {
        when (item) {
            is ContextMenuDivider -> {
                separator()
            }
            is ContextSubmenu -> {
                submenu(submenu = { contextItems(item.submenu()) }) { Text(item.label) }
            }
            is ContextMenuItemOption -> {
                selectableItemWithActionType(
                    selected = false,
                    onClick = item.onClick,
                    iconKey = item.icon,
                    actionType = item.actionType,
                    enabled = item.enabled,
                ) {
                    Text(item.label)
                }
            }
            else -> {
                selectableItem(selected = false, onClick = item.onClick) { Text(item.label) }
            }
        }
    }
}

/** A [ContextMenuItem] that represents a divider in a context menu. */
public object ContextMenuDivider : ContextMenuItem("---", {})

/**
 * A [ContextMenuItem] that represents a submenu in a context menu.
 *
 * @param label The text label of the submenu.
 * @param submenu A lambda that returns the list of [ContextMenuItem]s for the submenu.
 */
public class ContextSubmenu(label: String, public val submenu: () -> List<ContextMenuItem>) :
    ContextMenuItem(label, {})

/**
 * A [ContextMenuItem] that represents a selectable option in a context menu.
 *
 * @param icon The [IconKey] for the icon to display next to the item, or `null` if no icon should be displayed.
 * @param actionType The [ContextMenuItemOptionAction] that describes the action of this menu item, if any.
 * @param enabled Whether the menu item is enabled.
 * @param label The text label of the menu item.
 * @param action The action to perform when the menu item is clicked.
 */
public class ContextMenuItemOption(
    public val icon: IconKey? = null,
    public val actionType: ContextMenuItemOptionAction? = null,
    public val enabled: Boolean = true,
    label: String,
    action: () -> Unit,
) : ContextMenuItem(label, action) {
    @Deprecated("Kept for binary compat. Use the primary constructor instead.", level = DeprecationLevel.HIDDEN)
    public constructor(
        icon: IconKey? = null,
        actionType: ContextMenuItemOptionAction? = null,
        label: String,
        action: () -> Unit,
    ) : this(icon, actionType, enabled = true, label, action)
}

/** The predefined actions for [ContextMenuItemOption]s. */
public sealed class ContextMenuItemOptionAction {
    /** Represents a "Copy" action. */
    public data object CopyMenuItemOptionAction : ContextMenuItemOptionAction()

    /** Represents a "Paste" action. */
    public data object PasteMenuItemOptionAction : ContextMenuItemOptionAction()

    /** Represents a "Cut" action. */
    public data object CutMenuItemOptionAction : ContextMenuItemOptionAction()

    /** Represents a "Select All" action. */
    public data object SelectAllMenuItemOptionAction : ContextMenuItemOptionAction()
}

internal fun composeKeyEventToSwingKeyStroke(event: KeyEvent): KeyStroke? {
    val awtKeyCode = event.key.nativeKeyCode
    var modifiers = 0

    if (event.isCtrlPressed) modifiers = modifiers or InputEvent.CTRL_DOWN_MASK
    if (event.isMetaPressed) modifiers = modifiers or InputEvent.META_DOWN_MASK
    if (event.isAltPressed) modifiers = modifiers or InputEvent.ALT_DOWN_MASK
    if (event.isShiftPressed) modifiers = modifiers or InputEvent.SHIFT_DOWN_MASK

    return KeyStroke.getKeyStroke(awtKeyCode, modifiers, false)
}
