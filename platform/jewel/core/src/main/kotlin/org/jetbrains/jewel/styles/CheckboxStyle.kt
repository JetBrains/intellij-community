package org.jetbrains.jewel.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.IntelliJPainters
import org.jetbrains.jewel.IntelliJPalette
import org.jetbrains.jewel.ShapeStroke
import org.jetbrains.jewel.components.state.ButtonMouseState
import org.jetbrains.jewel.components.state.CheckboxState

typealias CheckboxStyle = ControlStyle<CheckboxAppearance, CheckboxState>

@Immutable
data class CheckboxAppearance(
    val textStyle: TextStyle = TextStyle.Default,

    val width: Dp = 16.dp,
    val height: Dp = 16.dp,
    val contentSpacing: Dp = 8.dp,

    val backgroundColor: Color = Color.Blue,
    val shapeStroke: ShapeStroke<*>? = ShapeStroke.SolidColor(1.dp, Color.Blue),
    val shape: Shape = RectangleShape,

    val interiorPainter: (@Composable () -> Painter)? = null,
    val symbolPadding: Dp = 2.dp,
    val baseLine: Dp = 14.dp,

    val haloStroke: ShapeStroke<*>? = null,
    val haloShape: Shape = shape
)

val LocalCheckboxStyle = compositionLocalOf<CheckboxStyle> { localNotProvided() }
val Styles.checkbox: CheckboxStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalCheckboxStyle.current

fun CheckboxStyle(
    palette: IntelliJPalette,
    painters: IntelliJPainters,
    controlTextStyle: TextStyle
) = CheckboxStyle {
    default {
        for (enabled in listOf(false, true)) {
            for (focused in listOf(false, true)) {
                for (toggleableState in listOf(ToggleableState.On, ToggleableState.Indeterminate, ToggleableState.Off)) {
                    val (painter, textStyle) = if (enabled) {
                        if (focused) {
                            when (toggleableState) {
                                ToggleableState.On -> painters.checkbox.selectedFocused
                                ToggleableState.Indeterminate -> painters.checkbox.indeterminateFocused
                                ToggleableState.Off -> painters.checkbox.unselectedFocused
                            } to controlTextStyle.copy(color = palette.text)
                        } else {
                            when (toggleableState) {
                                ToggleableState.On -> painters.checkbox.selected
                                ToggleableState.Indeterminate -> painters.checkbox.indeterminate
                                ToggleableState.Off -> painters.checkbox.unselected
                            } to controlTextStyle.copy(color = palette.text)
                        }
                    } else {
                        when (toggleableState) {
                            ToggleableState.On -> painters.checkbox.selectedDisabled
                            ToggleableState.Indeterminate -> painters.checkbox.indeterminateDisabled
                            ToggleableState.Off -> painters.checkbox.unselectedDisabled
                        } to controlTextStyle.copy(color = palette.textDisabled)
                    }

                    ButtonMouseState.values().forEach { buttonState ->
                        state(
                            CheckboxState(
                                toggleableState,
                                buttonState,
                                enabled = enabled,
                                focused = focused
                            ),
                            CheckboxAppearance(
                                interiorPainter = painter,
                                backgroundColor = Color.Transparent,
                                symbolPadding = 0.dp,
                                shapeStroke = null,
                                width = 19.dp,
                                height = 19.dp,
                                textStyle = textStyle
                            )
                        )
                    }
                }
            }
        }
    }
}
