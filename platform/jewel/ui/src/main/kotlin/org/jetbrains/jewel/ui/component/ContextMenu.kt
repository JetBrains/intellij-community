package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
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
import androidx.compose.ui.window.rememberCursorPositionProvider
import java.awt.event.InputEvent
import javax.swing.KeyStroke
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.theme.menuStyle

public object ContextMenuRepresentation : ContextMenuRepresentation {
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val isOpen = state.status is ContextMenuState.Status.Open

        if (isOpen) {
            ContextMenu(
                onDismissRequest = {
                    state.status = ContextMenuState.Status.Closed
                    true
                },
                style = JewelTheme.menuStyle,
            ) {
                contextItems(items)
            }
        }
    }
}

@Composable
internal fun ContextMenu(
    onDismissRequest: (InputMode) -> Boolean,
    modifier: Modifier = Modifier,
    focusable: Boolean = true,
    style: MenuStyle = JewelTheme.menuStyle,
    content: MenuScope.() -> Unit,
) {
    var focusManager: FocusManager? by remember { mutableStateOf(null) }
    var inputModeManager: InputModeManager? by remember { mutableStateOf(null) }
    val menuController = remember(onDismissRequest) { DefaultMenuController(onDismissRequest = onDismissRequest) }

    Popup(
        popupPositionProvider = rememberCursorPositionProvider(style.metrics.offset),
        onDismissRequest = { onDismissRequest(InputMode.Touch) },
        properties = PopupProperties(focusable = focusable),
        onPreviewKeyEvent = { false },
        onKeyEvent = {
            val currentFocusManager = checkNotNull(focusManager) { "FocusManager must not be null" }
            val currentInputModeManager = checkNotNull(inputModeManager) { "InputModeManager must not be null" }
            val swingKeyStroke = composeKeyEventToSwingKeyStroke(it)

            menuController.findAndExecuteShortcut(swingKeyStroke)
                ?: handlePopupMenuOnKeyEvent(it, currentFocusManager, currentInputModeManager, menuController)
        },
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

private fun MenuScope.contextItems(items: () -> List<ContextMenuItem>) {
    items().forEach { item ->
        when (item) {
            is ContextMenuDivider -> {
                separator()
            }
            is ContextSubmenu -> {
                submenu(submenu = { contextItems(item.submenu) }) { Text(item.label) }
            }
            is ContextMenuItemOption -> {
                selectableItemWithActionType(
                    selected = false,
                    onClick = item.onClick,
                    iconKey = item.icon,
                    actionType = item.actionType,
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

public object ContextMenuDivider : ContextMenuItem("---", {})

public class ContextSubmenu(label: String, public val submenu: () -> List<ContextMenuItem>) :
    ContextMenuItem(label, {})

public class ContextMenuItemOption(
    public val icon: IconKey? = null,
    public val actionType: ContextMenuItemOptionAction? = null,
    label: String,
    action: () -> Unit,
) : ContextMenuItem(label, action)

public sealed class ContextMenuItemOptionAction {
    public data object CopyMenuItemOptionAction : ContextMenuItemOptionAction()

    public data object PasteMenuItemOptionAction : ContextMenuItemOptionAction()

    public data object CutMenuItemOptionAction : ContextMenuItemOptionAction()

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
