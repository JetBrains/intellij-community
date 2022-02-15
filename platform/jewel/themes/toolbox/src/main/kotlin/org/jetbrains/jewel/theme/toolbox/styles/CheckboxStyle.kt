package org.jetbrains.jewel.theme.toolbox.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ShapeStroke
import org.jetbrains.jewel.components.state.ButtonMouseState
import org.jetbrains.jewel.components.state.CheckboxState
import org.jetbrains.jewel.styles.ControlStyle
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.styles.localNotProvided
import org.jetbrains.jewel.theme.toolbox.Palette
import org.jetbrains.jewel.theme.toolbox.ToolboxMetrics
import org.jetbrains.jewel.toBrush

typealias CheckboxStyle = ControlStyle<CheckboxAppearance, CheckboxState>

@Immutable
data class CheckboxAppearance(
    val textStyle: TextStyle = TextStyle.Default,

    val width: Dp = 16.dp,
    val height: Dp = 16.dp,
    val contentSpacing: Dp = 8.dp,

    val backgroundColor: Color = Color.Blue,
    val shapeStroke: ShapeStroke? = ShapeStroke(1.dp, Color.Blue.toBrush()),
    val shape: Shape = RectangleShape,

    val interiorPainter: PainterProvider? = null,
    val symbolPadding: Dp = 2.dp,
    val baseLine: Dp = 14.dp,

    val haloStroke: ShapeStroke? = null,
    val haloShape: Shape = shape,
)

typealias PainterProvider = @Composable () -> Painter

val LocalCheckboxStyle = compositionLocalOf<CheckboxStyle> { localNotProvided() }
val Styles.checkbox: CheckboxStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalCheckboxStyle.current

fun CheckboxStyle(palette: Palette, metrics: ToolboxMetrics) = CheckboxStyle {
    val offAppearance = CheckboxAppearance(
        textStyle = TextStyle(color = palette.text),
        backgroundColor = Color.Unspecified,
        width = metrics.base * 2,
        height = metrics.base * 2,
        shape = RectangleShape,
        shapeStroke = ShapeStroke(metrics.adornmentsThickness, palette.controlBackgroundDisabled.toBrush()),
        baseLine = metrics.base * 2 - metrics.base / 4,
        interiorPainter = null,
    )
    val indeterminateAppearance = offAppearance // TODO
    val onAppearance = offAppearance.copy(
        interiorPainter = { painterResource("jewel/checkmark.svg") }
    )
    default {
        for (focused in listOf(false, true)) {
            val haloStroke = if (focused)
                ShapeStroke(metrics.adornmentsThickness, palette.controlAdornmentsHover.toBrush())
            else
                null
            val disabledTextStyle = TextStyle(color = palette.textDisabled)
            state(
                CheckboxState(ToggleableState.On, enabled = false, focused = focused), onAppearance.copy(
                textStyle = disabledTextStyle,
                haloStroke = haloStroke,
            )
            )
            state(
                CheckboxState(ToggleableState.Off, enabled = false, focused = focused), offAppearance.copy(
                textStyle = disabledTextStyle,
                haloStroke = haloStroke,
            )
            )
            state(
                CheckboxState(ToggleableState.Indeterminate, enabled = false, focused = focused), indeterminateAppearance.copy(
                textStyle = disabledTextStyle,
                haloStroke = haloStroke,
            )
            )

            ButtonMouseState.values().forEach { buttonState ->
                val filledBackgroundColor = when (buttonState) {
                    ButtonMouseState.Hovered -> palette.controlBackgroundHover
                    ButtonMouseState.Pressed -> palette.controlBackground
                    else -> palette.controlBackground
                }
                state(
                    CheckboxState(ToggleableState.On, buttonState, focused = focused), onAppearance.copy(
                    backgroundColor = filledBackgroundColor,
                    haloStroke = haloStroke,
                    shapeStroke = ShapeStroke(metrics.adornmentsThickness, filledBackgroundColor.toBrush()),
                )
                )
                state(
                    CheckboxState(ToggleableState.Off, buttonState, focused = focused), offAppearance.copy(
                    haloStroke = haloStroke,
                    shapeStroke = ShapeStroke(
                        metrics.adornmentsThickness,
                        when (buttonState) {
                            ButtonMouseState.Hovered -> palette.controlBackgroundHover
                            ButtonMouseState.Pressed -> palette.controlBackground
                            else -> palette.controlAdornments
                        }.toBrush()
                    ),
                )
                )
                state(
                    CheckboxState(ToggleableState.Indeterminate, buttonState, focused = focused), indeterminateAppearance.copy(
                    backgroundColor = filledBackgroundColor,
                    haloStroke = haloStroke,
                    shapeStroke = ShapeStroke(metrics.adornmentsThickness, filledBackgroundColor.toBrush()),
                )
                )
            }
        }
    }
}
