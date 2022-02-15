package org.jetbrains.jewel.theme.toolbox.styles

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
import org.jetbrains.jewel.BottomLineShape
import org.jetbrains.jewel.Insets
import org.jetbrains.jewel.ShapeStroke
import org.jetbrains.jewel.components.state.TextFieldState
import org.jetbrains.jewel.styles.ControlStyle
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.styles.localNotProvided
import org.jetbrains.jewel.theme.toolbox.Palette
import org.jetbrains.jewel.theme.toolbox.ToolboxMetrics
import org.jetbrains.jewel.theme.toolbox.ToolboxTypography
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

fun TextFieldStyle(palette: Palette, metrics: ToolboxMetrics, typography: ToolboxTypography) = TextFieldStyle {
    val default = TextFieldAppearance(
        textStyle = typography.control.copy(palette.text),
        backgroundColor = Color.Unspecified,
        shape = RectangleShape,
        contentPadding = PaddingValues(0.dp, metrics.smallPadding),
        adornmentShape = BottomLineShape,
        adornmentStroke = ShapeStroke(
            metrics.adornmentsThickness,
            palette.controlAdornments.toBrush(),
            Insets(0.dp, metrics.adornmentsThickness / 2)
        ),
        minWidth = metrics.base * 4,
        minHeight = metrics.base * 2,
    )
    default {
        for (enabled in listOf(false, true)) {
            for (focused in listOf(false, true)) {
                for (hovered in listOf(false, true)) {
                    val appearance = when {
                        enabled -> when {
                            focused -> default.copy(
                                adornmentStroke = ShapeStroke(
                                    metrics.adornmentsThickness,
                                    palette.controlAdornmentsActive.toBrush(),
                                    Insets(0.dp, metrics.adornmentsThickness / 2)
                                )
                            )
                            else -> default
                        }
                        else -> default
                    }
                    state(
                        TextFieldState(focused, hovered, enabled),
                        appearance
                    )
                }
            }
        }
    }
}
