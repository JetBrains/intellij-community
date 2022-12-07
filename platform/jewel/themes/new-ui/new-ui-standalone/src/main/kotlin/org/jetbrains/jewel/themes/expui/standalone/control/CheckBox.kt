@file:Suppress("MatchingDeclarationName")

package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
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

class CheckBoxColors(
    override val normalAreaColors: AreaColors,
    override val selectionAreaColors: AreaColors,
    override val focusAreaColors: AreaColors,
    override val disabledAreaColors: AreaColors
) : AreaProvider, DisabledAreaProvider, FocusAreaProvider, SelectionAreaProvider {

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

val LocalCheckBoxColors = compositionLocalOf<CheckBoxColors> {
    LightTheme.CheckBoxColors
}

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckBoxColors = LocalCheckBoxColors.current
) {
    TriStateCheckbox(
        state = ToggleableState(checked),
        onClick = { onCheckedChange(!checked) },
        interactionSource = interactionSource,
        enabled = enabled,
        modifier = modifier,
        colors = colors
    )
}

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckBoxColors = LocalCheckBoxColors.current,
    content: @Composable () -> Unit
) {
    TriStateCheckbox(
        state = ToggleableState(checked),
        onClick = { onCheckedChange(!checked) },
        interactionSource = interactionSource,
        enabled = enabled,
        modifier = modifier,
        colors = colors,
        content = content
    )
}

@Composable
fun TriStateCheckbox(
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckBoxColors = LocalCheckBoxColors.current
) {
    val isFocused = remember { mutableStateOf(false) }
    colors.provideArea(enabled, isFocused.value, state != ToggleableState.Off) {
        CheckboxImpl(
            isFocused = isFocused.value,
            value = state,
            modifier = Modifier.onFocusEvent {
                isFocused.value = it.isFocused
            }.triStateToggleable(
                state = state,
                onClick = onClick,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null
            )
        )
    }
}

@Composable
fun TriStateCheckbox(
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: CheckBoxColors = LocalCheckBoxColors.current,
    content: @Composable () -> Unit
) {
    val isFocused = remember { mutableStateOf(false) }
    colors.provideArea(enabled, isFocused.value, state != ToggleableState.Off) {
        Row(
            modifier.onFocusEvent {
                isFocused.value = it.isFocused
            }.triStateToggleable(
                state = state,
                onClick = onClick,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CheckboxImpl(isFocused = isFocused.value, value = state)
            content()
        }
    }
}

@Suppress("MagicNumber")
private fun Checkmark() = ImageVector.Builder(
    name = "Checkmark",
    defaultWidth = 14.0.dp,
    defaultHeight = 14.0.dp,
    viewportWidth = 14.0f,
    viewportHeight = 14.0f
).apply {
    path(
        fill = SolidColor(Color(0xFFffffff)),
        stroke = null,
        strokeLineWidth = 0.0f,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Miter,
        strokeLineMiter = 4.0f,
        pathFillType = PathFillType.EvenOdd
    ) {
        moveTo(5.625f, 8.4267f)
        lineTo(9.5566f, 2.9336f)
        curveTo(9.5566f, 2.9336f, 10.1737f, 2.3242f, 10.8612f, 2.8242f)
        curveTo(11.4433f, 3.3867f, 10.998f, 4.0938f, 10.998f, 4.0938f)
        lineTo(6.3183f, 10.6445f)
        curveTo(6.3183f, 10.6445f, 5.9941f, 11.0f, 5.5839f, 11.0f)
        curveTo(5.1737f, 11.0f, 4.873f, 10.6445f, 4.873f, 10.6445f)
        lineTo(2.9394f, 7.7461f)
        curveTo(2.9394f, 7.7461f, 2.5683f, 6.9805f, 3.2558f, 6.4609f)
        curveTo(4.0605f, 6.0781f, 4.5605f, 6.8394f, 4.5605f, 6.8394f)
        lineTo(5.625f, 8.4267f)
        close()
    }
}.build()

@Suppress("MagicNumber")
private fun CheckmarkIndeterminate() = ImageVector.Builder(
    name = "CheckmarkIndeterminate",
    defaultWidth = 14.0.dp,
    defaultHeight = 14.0.dp,
    viewportWidth = 14.0f,
    viewportHeight = 14.0f
).apply {
    path(
        fill = SolidColor(Color(0xFFffffff)),
        stroke = null,
        strokeLineWidth = 0.0f,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Miter,
        strokeLineMiter = 4.0f,
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(3.7402f, 5.73f)
        lineTo(10.1402f, 5.73f)
        arcTo(1.0f, 1.0f, 0.0f, false, true, 11.1402f, 6.73f)
        lineTo(11.1402f, 7.23f)
        arcTo(1.0f, 1.0f, 0.0f, false, true, 10.1402f, 8.23f)
        lineTo(3.7402f, 8.23f)
        arcTo(1.0f, 1.0f, 0.0f, false, true, 2.7402f, 7.23f)
        lineTo(2.7402f, 6.73f)
        arcTo(1.0f, 1.0f, 0.0f, false, true, 3.7402f, 5.73f)
        close()
    }
}.build()

@Composable
private fun CheckboxImpl(
    isFocused: Boolean,
    value: ToggleableState,
    modifier: Modifier = Modifier
) {
    val icon = when (value) {
        ToggleableState.On -> rememberVectorPainter(Checkmark())
        ToggleableState.Indeterminate -> rememberVectorPainter(CheckmarkIndeterminate())
        else -> null
    }

    val colors = LocalAreaColors.current
    Canvas(modifier.wrapContentSize(Alignment.Center).requiredSize(14.dp)) {
        if (isFocused) {
            drawRoundRect(
                colors.focusColor,
                size = Size(18.dp.toPx(), 18.dp.toPx()),
                topLeft = Offset(-2.dp.toPx(), -2.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
        }
        drawRoundRect(colors.startBorderColor, cornerRadius = CornerRadius(2.dp.toPx()))
        drawRoundRect(
            colors.startBackground,
            size = Size(12.dp.toPx(), 12.dp.toPx()),
            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx())
        )
        if (icon != null) {
            with(icon) {
                14.dp.toPx()
                draw(Size(14.dp.toPx(), 14.dp.toPx()), colorFilter = ColorFilter.tint(colors.foreground))
            }
        }
    }
}
