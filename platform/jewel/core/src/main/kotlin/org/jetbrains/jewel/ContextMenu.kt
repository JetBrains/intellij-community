package org.jetbrains.jewel

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.rememberCursorPositionProvider

object IntelliJContextMenuRepresentation : ContextMenuRepresentation {

    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val isOpen = state.status is ContextMenuState.Status.Open

        if (isOpen) {
            ContextMenu(
                onDismissRequest = {
                    state.status = ContextMenuState.Status.Closed
                    true
                },
                defaults = IntelliJTheme.contextMenuDefaults
            ) {
                contextItems(items)
            }
        }
    }
}

@Composable
internal fun ContextMenu(
    onDismissRequest: (InputMode) -> Boolean,
    focusable: Boolean = true,
    modifier: Modifier = Modifier,
    defaults: MenuDefaults = IntelliJTheme.menuDefaults,
    offset: DpOffset = defaults.menuOffset(),
    content: MenuScope.() -> Unit
) {
    var focusManager: FocusManager? by mutableStateOf(null)
    var inputModeManager: InputModeManager? by mutableStateOf(null)
    val menuManager = remember(onDismissRequest) {
        MenuManager(
            onDismissRequest = onDismissRequest
        )
    }

    Popup(
        focusable = focusable,
        onDismissRequest = {
            onDismissRequest(InputMode.Touch)
        },
        popupPositionProvider = rememberCursorPositionProvider(offset),
        onKeyEvent = {
            handlePopupMenuOnKeyEvent(it, focusManager!!, inputModeManager!!, menuManager)
        }
    ) {
        focusManager = LocalFocusManager.current
        inputModeManager = LocalInputModeManager.current

        CompositionLocalProvider(
            LocalMenuManager provides menuManager,
            LocalMenuDefaults provides defaults
        ) {
            MenuContent(
                modifier = modifier,
                content = content
            )
        }
    }
}

private fun MenuScope.contextItems(items: () -> List<ContextMenuItem>) {
    items().forEach { item ->
        when (item) {
            is ContextMenuDivider -> {
                divider()
            }

            is ContextSubmenu -> {
                submenu(submenu = {
                    contextItems(item.submenu)
                }) {
                    Text(item.label)
                }
            }

            else -> {
                selectableItem(
                    selected = false,
                    onClick = item.onClick
                ) {
                    Text(item.label)
                }
            }
        }
    }
}

object ContextMenuDivider : ContextMenuItem("---", {})

class ContextSubmenu(
    label: String,
    val submenu: () -> List<ContextMenuItem>
) : ContextMenuItem(label, {})

internal val LocalContextMenuDefaults = staticCompositionLocalOf<MenuDefaults> {
    error("No ContextMenuDefaults provided")
}
