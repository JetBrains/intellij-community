package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.rememberCursorPositionProvider
import org.jetbrains.jewel.themes.expui.standalone.style.AreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.AreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.FocusAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.HoverAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalFocusAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalHoverAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalNormalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalPressedAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.PressedAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.areaBackground
import org.jetbrains.jewel.themes.expui.standalone.style.areaBorder
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme
import java.awt.event.KeyEvent

class ContextMenuColors(
    override val normalAreaColors: AreaColors,
    override val hoverAreaColors: AreaColors,
    override val pressedAreaColors: AreaColors,
    override val focusAreaColors: AreaColors,
) : AreaProvider, HoverAreaProvider, PressedAreaProvider, FocusAreaProvider {

    @Composable
    fun provideArea(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalNormalAreaColors provides normalAreaColors,
            LocalAreaColors provides normalAreaColors,
            LocalHoverAreaColors provides hoverAreaColors,
            LocalPressedAreaColors provides pressedAreaColors,
            LocalFocusAreaColors provides focusAreaColors,
            content = content
        )
    }
}

val LocalContextMenuColors = compositionLocalOf {
    LightTheme.ContextMenuColors
}

class JbContextMenuRepresentation(private val colors: ContextMenuColors) : ContextMenuRepresentation {

    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val isOpen = state.status is ContextMenuState.Status.Open
        ContextMenu(
            isOpen = isOpen,
            items = items,
            onDismissRequest = { state.status = ContextMenuState.Status.Closed },
            colors = colors,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContextMenu(
    isOpen: Boolean,
    items: () -> List<ContextMenuItem>,
    onDismissRequest: () -> Unit,
    colors: ContextMenuColors = LocalContextMenuColors.current,
) {
    if (isOpen) {
        var focusManager: FocusManager? by mutableStateOf(null)
        var inputModeManager: InputModeManager? by mutableStateOf(null)
        Popup(
            focusable = true,
            onDismissRequest = onDismissRequest,
            popupPositionProvider = rememberCursorPositionProvider(),
            onKeyEvent = {
                if (it.type == KeyEventType.KeyDown) {
                    when (it.key.nativeKeyCode) {
                        KeyEvent.VK_ESCAPE -> {
                            onDismissRequest()
                            true
                        }

                        KeyEvent.VK_DOWN -> {
                            inputModeManager!!.requestInputMode(InputMode.Keyboard)
                            focusManager!!.moveFocus(FocusDirection.Next)
                            true
                        }

                        KeyEvent.VK_UP -> {
                            inputModeManager!!.requestInputMode(InputMode.Keyboard)
                            focusManager!!.moveFocus(FocusDirection.Previous)
                            true
                        }

                        else -> false
                    }
                } else {
                    false
                }
            },
        ) {
            focusManager = LocalFocusManager.current
            inputModeManager = LocalInputModeManager.current
            colors.provideArea {
                Box(
                    modifier = Modifier.shadow(12.dp).areaBorder().areaBackground()
                        .sizeIn(maxHeight = 600.dp, minWidth = 72.dp).padding(6.dp).width(IntrinsicSize.Max)
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier.verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items().forEach { item ->
                            MenuItemContent(onClick = {
                                onDismissRequest()
                                item.onClick()
                            }) {
                                Label(text = item.label)
                            }
                        }
                    }
                    Box(modifier = Modifier.matchParentSize()) {
                        VerticalScrollbar(
                            rememberScrollbarAdapter(scrollState),
                            modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItemContent(
    onClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp),
    shape: Shape = RoundedCornerShape(3.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val focused = remember { mutableStateOf(false) }
    val focusedColors = LocalFocusAreaColors.current
    Row(
        modifier = Modifier.drawWithCache {
            onDrawBehind {
                if (focused.value) {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    drawOutline(outline, focusedColors.startBackground)
                }
            }
        }.onFocusEvent {
            focused.value = it.isFocused
        }.clickable(
            enabled = true,
            onClick = onClick,
            interactionSource = interactionSource,
            indication = HoverOrPressedIndication(shape)
        ).fillMaxWidth().padding(contentPadding).defaultMinSize(minHeight = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}
