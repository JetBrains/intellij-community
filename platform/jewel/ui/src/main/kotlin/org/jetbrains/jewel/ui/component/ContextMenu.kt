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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberCursorPositionProvider
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.MenuStyle
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
    var focusManager: FocusManager? by mutableStateOf(null)
    var inputModeManager: InputModeManager? by mutableStateOf(null)
    val menuManager = remember(onDismissRequest) { MenuManager(onDismissRequest = onDismissRequest) }

    Popup(
        popupPositionProvider = rememberCursorPositionProvider(style.metrics.offset),
        onDismissRequest = { onDismissRequest(InputMode.Touch) },
        properties = PopupProperties(focusable = focusable),
        onPreviewKeyEvent = { false },
        onKeyEvent = {
            val currentFocusManager = checkNotNull(focusManager) { "FocusManager must not be null" }
            val currentInputModeManager = checkNotNull(inputModeManager) { "InputModeManager must not be null" }
            handlePopupMenuOnKeyEvent(it, currentFocusManager, currentInputModeManager, menuManager)
        },
    ) {
        focusManager = LocalFocusManager.current
        inputModeManager = LocalInputModeManager.current

        CompositionLocalProvider(LocalMenuManager provides menuManager) {
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
            else -> {
                selectableItem(selected = false, onClick = item.onClick) { Text(item.label) }
            }
        }
    }
}

public object ContextMenuDivider : ContextMenuItem("---", {})

public class ContextSubmenu(label: String, public val submenu: () -> List<ContextMenuItem>) :
    ContextMenuItem(label, {})
