@file:Suppress("MatchingDeclarationName")
package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.themes.expui.standalone.style.AreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.AreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.DisabledAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.FocusAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalDisabledAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalFocusAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalNormalAreaColors

class ComboBoxColors(
    override val normalAreaColors: AreaColors,
    override val focusAreaColors: AreaColors,
    override val disabledAreaColors: AreaColors,
    val dropdownMenuColors: DropdownMenuColors,
) : AreaProvider, FocusAreaProvider, DisabledAreaProvider {

    @Composable
    fun provideArea(enabled: Boolean, focused: Boolean, content: @Composable () -> Unit) {
        val currentAreaColor = when {
            !enabled -> disabledAreaColors
            focused -> focusAreaColors
            else -> normalAreaColors
        }

        CompositionLocalProvider(
            LocalAreaColors provides currentAreaColor,
            LocalNormalAreaColors provides normalAreaColors,
            LocalFocusAreaColors provides focusAreaColors,
            LocalDisabledAreaColors provides disabledAreaColors,
            LocalDropdownMenuColors provides dropdownMenuColors,
            content = content
        )
    }
}

val LocalComboBoxColors = compositionLocalOf<ComboBoxColors> {
    error("No ComboBoxColors provided")
}

@Composable
fun <T> ComboBox(
    items: List<T>,
    value: T,
    onValueChange: ((T) -> Unit)? = null,
    modifier: Modifier = Modifier,
    menuModifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRender: @Composable (T) -> Unit = { Label("$it") },
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: ComboBoxColors = LocalComboBoxColors.current,
) {
    val isFocused = remember { mutableStateOf(false) }
    var menuOpened by remember { mutableStateOf(false) }
    colors.provideArea(enabled, isFocused.value) {
        val areaColors = LocalAreaColors.current
        Box {
            Box(
                Modifier.defaultMinSize(72.dp, 24.dp).drawWithCache {
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
                }.onFocusEvent {
                    isFocused.value = it.isFocused
                }.clickable(
                    interactionSource = interactionSource, indication = null, enabled = enabled, onClick = {
                    menuOpened = true
                }, role = Role.Button
                ).padding(6.dp, 3.dp).then(modifier),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    valueRender(value)
                }

                Icon("icons/buttonDropTriangle.svg", modifier = Modifier.align(Alignment.CenterEnd))
            }

            DropdownMenu(menuOpened, { menuOpened = false }, modifier = menuModifier) {
                items.forEach { item ->
                    val focusRequester = remember { FocusRequester() }
                    DropdownMenuItem(
                        onClick = {
                            if (value != item) {
                                onValueChange?.invoke(item)
                            }
                            menuOpened = false
                        },
                        Modifier.focusRequester(focusRequester).onFocusEvent {
                            if (it.isFocused && value != item) {
                                onValueChange?.invoke(item)
                            }
                        }
                    ) {
                        valueRender(item)
                    }
                    LaunchedEffect(menuOpened) {
                        if (menuOpened && value == item) {
                            focusRequester.requestFocus()
                        }
                    }
                }
            }
        }
    }
}
