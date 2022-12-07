@file:Suppress("MatchingDeclarationName")
package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme

class ButtonColors(
    override val normalAreaColors: AreaColors,
    override val focusAreaColors: AreaColors,
    override val disabledAreaColors: AreaColors,
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
            content = content
        )
    }
}

val LocalPrimaryButtonColors = compositionLocalOf {
    LightTheme.PrimaryButtonColors
}

val LocalOutlineButtonColors = compositionLocalOf {
    LightTheme.OutlineButtonColors
}

@Composable
fun OutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: ButtonColors = LocalOutlineButtonColors.current,
    content: @Composable RowScope.() -> Unit,
) {
    ButtonImpl(onClick, modifier, enabled, interactionSource, colors, content)
}

@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: ButtonColors = LocalPrimaryButtonColors.current,
    content: @Composable RowScope.() -> Unit,
) {
    ButtonImpl(onClick, modifier, enabled, interactionSource, colors, content)
}

@Composable
private fun ButtonImpl(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: ButtonColors,
    content: @Composable RowScope.() -> Unit,
) {
    val isFocused = remember { mutableStateOf(false) }
    colors.provideArea(enabled, isFocused.value) {
        val areaColors = LocalAreaColors.current
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
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
                role = Role.Button
            ).then(modifier),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(14.dp, 3.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
            }
        }
    }
}
