package org.jetbrains.jewel.theme.intellij.styles

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.jetbrains.jewel.Insets
import org.jetbrains.jewel.ShapeStroke
import org.jetbrains.jewel.components.state.TextFieldState
import org.jetbrains.jewel.styles.ControlStyle
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.styles.localNotProvided
import org.jetbrains.jewel.theme.intellij.IntelliJMetrics
import org.jetbrains.jewel.theme.intellij.IntelliJPalette
import org.jetbrains.jewel.toBrush

typealias TextFieldStyle = ControlStyle<TextFieldAppearance, TextFieldState>

data class TextFieldAppearance(
    val textStyle: TextStyle = TextStyle.Default,
    val backgroundColor: Color,
    val shapeStroke: ShapeStroke? = null,
    val shape: Shape,

    val adornmentStroke: ShapeStroke? = null,
    val adornmentShape: Shape? = null,

    val cursorBrush: Brush = SolidColor(Color.Black),
    val contentPadding: PaddingValues,

    val haloStroke: ShapeStroke? = null,
    val haloShape: Shape = shape,

    val minWidth: Dp = Dp.Unspecified,
    val minHeight: Dp = Dp.Unspecified,
)

val LocalTextFieldStyle = compositionLocalOf<TextFieldStyle> { localNotProvided() }
val Styles.textField: TextFieldStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalTextFieldStyle.current

fun TextFieldStyle(
    palette: IntelliJPalette,
    metrics: IntelliJMetrics,
    textStyle: TextStyle
) = TextFieldStyle {
    val defaultAppearance = TextFieldAppearance(
        textStyle = textStyle.copy(palette.textField.foreground),
        backgroundColor = palette.textField.background,
        shape = RectangleShape,
        contentPadding = PaddingValues(10.dp, 7.dp),
        cursorBrush = palette.text.toBrush(),
        shapeStroke = ShapeStroke(
            1.dp,
            palette.controlStroke.toBrush(),
            Insets(1.dp)
        ),
        haloShape = RoundedCornerShape(metrics.controlFocusHaloArc),
        minWidth = 8.dp * 8,
        minHeight = 8.dp * 2,
    )

    val disabledAppearance = defaultAppearance.copy(
        textStyle = defaultAppearance.textStyle.copy(color = palette.textField.foregroundDisabled),
        backgroundColor = palette.textField.backgroundDisabled
    )

    val focusedAppearance = defaultAppearance.copy(
        shapeStroke = ShapeStroke(
            1.dp,
            palette.controlStrokeFocused.toBrush(),
            Insets(1.dp)
        ),
        haloStroke = ShapeStroke(
            metrics.controlFocusHaloWidth,
            palette.controlFocusHalo.toBrush(),
            Insets((-1).dp)
        )
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
                TextFieldState(
                    focused = focused,
                    hovered = hovered,
                    enabled = enabled
                ),
                appearance
            )
        }
    }

    variation(IntelliJTextFieldVariations.Error) {
        allStateCombinations { enabled, focused, hovered ->
            val appearance = if (enabled) {
                defaultAppearance.copy(
                    shapeStroke = ShapeStroke(
                        1.dp,
                        palette.controlHaloError.toBrush(),
                        Insets(1.dp)
                    ),
                    haloStroke = ShapeStroke(
                        metrics.controlFocusHaloWidth,
                        palette.controlInactiveHaloError.toBrush(),
                        Insets((-1).dp)
                    )
                )
            } else {
                disabledAppearance
            }

            state(
                TextFieldState(
                    focused = focused,
                    hovered = hovered,
                    enabled = enabled
                ),
                appearance
            )
        }
    }

    variation(IntelliJTextFieldVariations.Warning) {
        allStateCombinations { enabled, focused, hovered ->
            val appearance = when {
                enabled -> defaultAppearance.copy(
                    shapeStroke = ShapeStroke(
                        1.dp,
                        palette.controlHaloWarning.toBrush(),
                        Insets(1.dp)
                    ),
                    haloStroke = ShapeStroke(
                        metrics.controlFocusHaloWidth,
                        palette.controlInactiveHaloWarning.toBrush(),
                        Insets((-1).dp)
                    )
                )
                else -> disabledAppearance
            }

            state(
                TextFieldState(
                    focused = focused,
                    hovered = hovered,
                    enabled = enabled
                ),
                appearance
            )
        }
    }

    variation(IntelliJTextFieldVariations.Search) {
        allStateCombinations { enabled, focused, hovered ->
            val appearance = when {
                enabled -> when {
                    focused -> focusedAppearance.copy(shape = RoundedCornerShape(metrics.controlArc))
                    else -> defaultAppearance.copy(shape = RoundedCornerShape(metrics.controlArc))
                }
                else -> disabledAppearance.copy(shape = RoundedCornerShape(metrics.controlArc))
            }

            state(
                TextFieldState(
                    focused = focused,
                    hovered = hovered,
                    enabled = enabled
                ),
                appearance
            )
        }
    }
}

private fun ControlStyle.ControlVariationBuilder<TextFieldAppearance, TextFieldState>.allStateCombinations(
    action: ControlStyle.ControlVariationBuilder<TextFieldAppearance, TextFieldState>.(enabled: Boolean, focused: Boolean, hovered: Boolean) -> Unit
) {
    for (enabled in listOf(false, true)) {
        for (focused in listOf(false, true)) {
            for (hovered in listOf(false, true)) {
                action(enabled, focused, hovered)
            }
        }
    }
}

enum class IntelliJTextFieldVariations {
    Error,
    Search,
    Warning
}
