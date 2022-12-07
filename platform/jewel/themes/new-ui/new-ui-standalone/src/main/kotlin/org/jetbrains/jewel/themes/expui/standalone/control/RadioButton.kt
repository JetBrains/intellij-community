@file:Suppress("MatchingDeclarationName")
package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
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
import org.jetbrains.jewel.themes.expui.standalone.style.LocalSelectionAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.SelectionAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme

class RadioButtonColors(
    override val normalAreaColors: AreaColors,
    override val selectionAreaColors: AreaColors,
    override val focusAreaColors: AreaColors,
    override val disabledAreaColors: AreaColors,
) : AreaProvider, SelectionAreaProvider, FocusAreaProvider, DisabledAreaProvider {

    @Composable
    fun provideArea(enabled: Boolean, focused: Boolean, selected: Boolean, content: @Composable () -> Unit) {
        val currentColors = when {
            !enabled -> disabledAreaColors
            focused -> focusAreaColors
            selected -> selectionAreaColors
            else -> normalAreaColors
        }

        CompositionLocalProvider(
            LocalAreaColors provides currentColors,
            LocalNormalAreaColors provides normalAreaColors,
            LocalSelectionAreaColors provides selectionAreaColors,
            LocalFocusAreaColors provides focusAreaColors,
            LocalDisabledAreaColors provides disabledAreaColors,
            content = content
        )
    }
}

val LocalRadioButtonColors = compositionLocalOf {
    LightTheme.RadioButtonColors
}

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: RadioButtonColors = LocalRadioButtonColors.current,
) {
    val isFocused = remember { mutableStateOf(false) }
    colors.provideArea(enabled, isFocused.value, selected) {
        RadioButtonImpl(
            isFocused.value, selected,
            modifier = modifier.onFocusEvent {
                isFocused.value = it.isFocused
            }.selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton
            )
        )
    }
}

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: RadioButtonColors = LocalRadioButtonColors.current,
    content: @Composable RowScope.() -> Unit = {},
) {
    val isFocused = remember { mutableStateOf(false) }
    colors.provideArea(enabled, isFocused.value, selected) {
        Row(
            modifier = modifier.onFocusEvent {
                isFocused.value = it.isFocused
            }.selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton
            ),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RadioButtonImpl(isFocused.value, selected)
            content()
        }
    }
}

@Composable
private fun RadioButtonImpl(
    isFocused: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAreaColors.current
    Canvas(modifier.wrapContentSize(Alignment.Center).requiredSize(15.dp)) {
        if (isFocused) {
            drawCircle(
                colors.focusColor,
                radius = 9.5.dp.toPx(),
            )
        }
        drawCircle(
            colors.startBorderColor,
            radius = 7.5.dp.toPx(),
        )
        drawCircle(
            colors.startBackground,
            radius = 6.5.dp.toPx(),
        )
        if (selected) {
            drawCircle(
                colors.foreground,
                radius = 2.5.dp.toPx(),
            )
        }
    }
}
