package org.jetbrains.jewel.styles

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.Insets
import org.jetbrains.jewel.IntelliJMetrics
import org.jetbrains.jewel.IntelliJPalette
import org.jetbrains.jewel.ShapeStroke
import org.jetbrains.jewel.components.state.TextAreaState
import org.jetbrains.jewel.toBrush

typealias TextAreaStyle = ControlStyle<TextAreaAppearance, TextAreaState>

data class TextAreaAppearance(
    val textStyle: TextStyle = TextStyle.Default,
    val backgroundColor: Color,
    val shapeStroke: ShapeStroke<*>? = null,
    val shape: Shape,

    val cursorBrush: Brush = SolidColor(Color.Black),
    val contentPadding: PaddingValues,

    val haloStroke: ShapeStroke<*>? = null,

    val height: Dp = Dp.Unspecified,
    val width: Dp = Dp.Unspecified
)

val LocalTextAreaStyle = compositionLocalOf<TextAreaStyle> { localNotProvided() }
val Styles.textArea: TextAreaStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalTextAreaStyle.current

fun TextAreaStyle(
    palette: IntelliJPalette,
    metrics: IntelliJMetrics,
    textStyle: TextStyle
) = TextAreaStyle {
    val defaultAppearance = TextAreaAppearance(
        textStyle = textStyle.copy(
            color = palette.textField.foreground,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        backgroundColor = palette.textField.background,
        shape = RectangleShape,
        contentPadding = PaddingValues(7.dp, 4.dp),
        cursorBrush = palette.text.toBrush(),
        shapeStroke = ShapeStroke.SolidColor(1.dp, palette.controlStroke, Insets(0.dp)),
        width = 270.dp,
        height = 55.dp
    )

    val disabledAppearance = defaultAppearance.copy(
        textStyle = defaultAppearance.textStyle.copy(color = palette.textField.foregroundDisabled),
        backgroundColor = palette.textField.backgroundDisabled
    )

    val focusedAppearance = defaultAppearance.copy(
        shapeStroke = ShapeStroke.SolidColor(1.dp, palette.controlStrokeFocused, Insets(0.dp))
    )

    default {
        allStateCombinations { enabled, focused, hovered ->
            val appearance = when {
                enabled -> when {
                    focused -> focusedAppearance
                    else -> defaultAppearance
                }
                else -> disabledAppearance
            }

            state(
                TextAreaState(
                    enabled = enabled,
                    focused = focused,
                    hovered = hovered
                ),
                appearance
            )
        }
    }

    variation(IntellijTextAreaVariations.Error) {
        allStateCombinations { enabled, focused, hovered ->
            val appearance = if (enabled) {
                defaultAppearance.copy(
                    shapeStroke = ShapeStroke.SolidColor(1.dp, palette.controlHaloError, Insets(1.dp)),
                    haloStroke = ShapeStroke.SolidColor(metrics.controlFocusHaloWidth, palette.controlInactiveHaloError, Insets((-1).dp))
                )
            } else {
                disabledAppearance
            }

            state(
                TextAreaState(
                    enabled = enabled,
                    focused = focused,
                    hovered = hovered
                ),
                appearance
            )
        }
    }

    variation(IntellijTextAreaVariations.Warning) {
        allStateCombinations { enabled, focused, hovered ->
            val appearance = when {
                enabled -> defaultAppearance.copy(
                    shapeStroke = ShapeStroke.SolidColor(1.dp, palette.controlHaloWarning, Insets(1.dp)),
                    haloStroke = ShapeStroke.SolidColor(metrics.controlFocusHaloWidth, palette.controlInactiveHaloWarning, Insets((-1).dp))
                )

                else -> disabledAppearance
            }

            state(
                TextAreaState(
                    enabled = enabled,
                    focused = focused,
                    hovered = hovered
                ),
                appearance
            )
        }
    }
}

private fun ControlStyle.ControlVariationBuilder<TextAreaAppearance, TextAreaState>.allStateCombinations(
    action: ControlStyle.ControlVariationBuilder<TextAreaAppearance, TextAreaState>.(enabled: Boolean, focused: Boolean, hovered: Boolean) -> Unit
) {
    for (enabled in listOf(false, true)) {
        for (focused in listOf(false, true)) {
            for (hovered in listOf(false, true)) {
                action(enabled, focused, hovered)
            }
        }
    }
}

enum class IntellijTextAreaVariations {
    Error,
    Warning
}
