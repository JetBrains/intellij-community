package org.jetbrains.jewel.theme.toolbox.styles

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ShapeStroke
import org.jetbrains.jewel.components.state.ButtonMouseState
import org.jetbrains.jewel.styles.ControlStyle
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.styles.localNotProvided
import org.jetbrains.jewel.theme.toolbox.Palette
import org.jetbrains.jewel.theme.toolbox.ToolboxMetrics
import org.jetbrains.jewel.toBrush

typealias RadioButtonStyle = ControlStyle<RadioButtonAppearance, RadioButtonState>

data class RadioButtonState(
    val checked: Boolean,
    val mouse: ButtonMouseState = ButtonMouseState.None,
    val enabled: Boolean = true,
    val focused: Boolean = false,
)

@Immutable
data class RadioButtonAppearance(
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

val LocalRadioButtonStyle = compositionLocalOf<RadioButtonStyle> { localNotProvided() }
val Styles.radioButton: RadioButtonStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalRadioButtonStyle.current

fun RadioButtonStyle(palette: Palette, metrics: ToolboxMetrics) = RadioButtonStyle {
    val offAppearance = RadioButtonAppearance(
        textStyle = TextStyle(color = palette.text),
        backgroundColor = Color.Unspecified,
        width = metrics.base * 2,
        height = metrics.base * 2,
        shape = CircleShape,
        shapeStroke = ShapeStroke(metrics.adornmentsThickness, palette.controlBackgroundDisabled.toBrush()),
        baseLine = metrics.base * 2 - metrics.base / 4,
        symbolPadding = metrics.base / 2,
        interiorPainter = null,
    )
    val onAppearance = offAppearance.copy(
        interiorPainter = { painterResource("jewel/radiomark.svg") }
    )
    default {
        for (focused in listOf(false, true)) {
            val haloStroke = if (focused)
                ShapeStroke(metrics.adornmentsThickness, palette.controlAdornmentsHover.toBrush())
            else
                null
            val disabledTextStyle = TextStyle(color = palette.textDisabled)
            state(
                RadioButtonState(true, enabled = false, focused = focused), onAppearance.copy(
                textStyle = disabledTextStyle,
                haloStroke = haloStroke,
            )
            )
            state(
                RadioButtonState(false, enabled = false, focused = focused), offAppearance.copy(
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
                    RadioButtonState(checked = true, mouse = buttonState, focused = focused),
                    onAppearance.copy(
                        backgroundColor = filledBackgroundColor,
                        haloStroke = haloStroke,
                        shapeStroke = ShapeStroke(metrics.adornmentsThickness, filledBackgroundColor.toBrush()),
                    )
                )
                state(
                    RadioButtonState(checked = false, mouse = buttonState, focused = focused),
                    offAppearance.copy(
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
            }
        }
    }
}
