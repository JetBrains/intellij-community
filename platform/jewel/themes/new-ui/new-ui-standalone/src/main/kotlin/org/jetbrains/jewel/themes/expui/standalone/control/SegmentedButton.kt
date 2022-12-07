package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.themes.expui.standalone.style.AreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.AreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.FocusAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalFocusAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalHoverAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalNormalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalPressedAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalSelectionAreaColors
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme

class SegmentedButtonColors(
    override val normalAreaColors: AreaColors,
    override val focusAreaColors: AreaColors,
    val itemNormalAreaColors: AreaColors,
    val itemHoverAreaColors: AreaColors,
    val itemPressedAreaColors: AreaColors,
    val itemSelectionAreaColors: AreaColors,
    val itemSelectedFocusAreaColors: AreaColors,
) : AreaProvider, FocusAreaProvider {

    @Composable
    fun provideArea(focused: Boolean, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalAreaColors provides if (focused) focusAreaColors else normalAreaColors,
            LocalNormalAreaColors provides normalAreaColors,
            LocalFocusAreaColors provides focusAreaColors,
            content = content
        )
    }

    @Composable
    fun provideItemArea(
        selected: Boolean,
        focused: Boolean,
        hover: Boolean,
        pressed: Boolean,
        content: @Composable () -> Unit,
    ) {
        val currentColors = when {
            selected -> if (focused) itemSelectedFocusAreaColors else itemSelectionAreaColors
            pressed -> itemPressedAreaColors
            hover -> itemHoverAreaColors
            else -> itemNormalAreaColors
        }

        CompositionLocalProvider(
            LocalAreaColors provides currentColors,
            LocalNormalAreaColors provides itemNormalAreaColors,
            LocalFocusAreaColors provides itemSelectedFocusAreaColors,
            LocalHoverAreaColors provides itemHoverAreaColors,
            LocalPressedAreaColors provides itemPressedAreaColors,
            LocalSelectionAreaColors provides itemSelectionAreaColors,
            content = content
        )
    }
}

val LocalSegmentedButtonColors = compositionLocalOf {
    LightTheme.SegmentedButtonColors
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SegmentedButton(
    itemCount: Int,
    selectedIndex: Int,
    onValueChange: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    colors: SegmentedButtonColors = LocalSegmentedButtonColors.current,
    valueRender: @Composable BoxScope.(Int) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused = interactionSource.collectIsFocusedAsState()
    val parentFocusRequester = remember { FocusRequester() }

    colors.provideArea(isFocused.value) {
        val areaColors = LocalAreaColors.current
        Row(
            modifier.selectableGroup().drawWithCache {
                onDrawBehind {
                    if (isFocused.value) {
                        drawRoundRect(
                            areaColors.focusColor,
                            size = Size(size.width + 4.dp.toPx(), size.height + 4.dp.toPx()),
                            topLeft = Offset(-2.dp.toPx(), -2.dp.toPx()),
                            cornerRadius = CornerRadius(5.dp.toPx())
                        )
                    }
                    drawRoundRect(areaColors.startBorderColor, cornerRadius = CornerRadius(3.dp.toPx()))
                    drawRoundRect(
                        areaColors.startBackground,
                        size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }.onKeyEvent {
                if (it.type != KeyEventType.KeyUp) return@onKeyEvent false
                if (!isFocused.value) return@onKeyEvent false
                when (it.key) {
                    Key.DirectionLeft -> {
                        val target = selectedIndex - 1
                        if (target in 0 until itemCount) {
                            onValueChange?.invoke(target)
                        }
                        return@onKeyEvent true
                    }

                    Key.DirectionRight -> {
                        val target = selectedIndex + 1
                        if (target in 0 until itemCount) {
                            onValueChange?.invoke(target)
                        }
                        return@onKeyEvent true
                    }
                }
                false
            }.focusRequester(parentFocusRequester).focusable(true, interactionSource = interactionSource),
            horizontalArrangement = Arrangement.spacedBy((-1).dp)
        ) {
            repeat(itemCount) {
                val itemInteractionSource = remember(it) { MutableInteractionSource() }
                val hover = itemInteractionSource.collectIsHoveredAsState()
                val pressed = itemInteractionSource.collectIsPressedAsState()

                colors.provideItemArea(selectedIndex == it, isFocused.value, hover.value, pressed.value) {
                    val current = LocalAreaColors.current
                    val focusManager = LocalFocusManager.current

                    Box(
                        modifier = Modifier.drawWithCache {
                            onDrawBehind {
                                drawRoundRect(current.startBorderColor, cornerRadius = CornerRadius(3.dp.toPx()))
                                drawRoundRect(
                                    current.startBackground,
                                    size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                            }
                        }.focusProperties {
                            this.canFocus = false
                        }.selectable(selectedIndex == it, onClick = {
                            onValueChange?.invoke(it)
                            if (!isFocused.value) {
                                focusManager.clearFocus()
                            }
                        }, interactionSource = itemInteractionSource, indication = null, role = Role.RadioButton)
                    ) {
                        Box(modifier = Modifier.padding(13.dp, 4.dp)) {
                            valueRender(it)
                        }
                    }
                }
            }
        }
    }
}
