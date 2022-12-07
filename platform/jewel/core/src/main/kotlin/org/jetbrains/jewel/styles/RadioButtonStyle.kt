package org.jetbrains.jewel.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.IntelliJPainters
import org.jetbrains.jewel.IntelliJPalette
import org.jetbrains.jewel.ShapeStroke
import org.jetbrains.jewel.components.state.ButtonMouseState

typealias RadioButtonStyle = ControlStyle<RadioButtonAppearance, RadioButtonState>

data class RadioButtonState(
    val checked: Boolean,
    val mouse: ButtonMouseState = ButtonMouseState.None,
    val enabled: Boolean = true,
    val focused: Boolean = false
)

@Immutable
data class RadioButtonAppearance(
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

val LocalRadioButtonStyle = compositionLocalOf<RadioButtonStyle> { localNotProvided() }
val Styles.radioButton: RadioButtonStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalRadioButtonStyle.current

fun RadioButtonStyle(
    palette: IntelliJPalette,
    painters: IntelliJPainters,
    controlTextStyle: TextStyle
) = RadioButtonStyle {
    default {
        for (enabled in listOf(false, true)) {
            for (focused in listOf(false, true)) {
                for (checked in listOf(false, true)) {
                    val (painter, textStyle) = if (enabled) {
                        if (focused) {
                            when (checked) {
                                true -> painters.radioButton.selectedFocused
                                false -> painters.radioButton.unselectedFocused
                            } to controlTextStyle.copy(color = palette.text)
                        } else {
                            when (checked) {
                                true -> painters.radioButton.selected
                                false -> painters.radioButton.unselected
                            } to controlTextStyle.copy(color = palette.text)
                        }
                    } else {
                        when (checked) {
                            true -> painters.radioButton.selectedDisabled
                            false -> painters.radioButton.unselectedDisabled
                        } to controlTextStyle.copy(color = palette.textDisabled)
                    }

                    ButtonMouseState.values().forEach { buttonState ->
                        state(
                            RadioButtonState(
                                checked,
                                buttonState,
                                enabled = enabled,
                                focused = focused
                            ),
                            RadioButtonAppearance(
                                textStyle = textStyle,
                                interiorPainter = painter,
                                backgroundColor = Color.Transparent,
                                symbolPadding = 0.dp,
                                shapeStroke = null,
                                width = 19.dp,
                                height = 19.dp
                            )
                        )
                    }
                }
            }
        }
    }
}
