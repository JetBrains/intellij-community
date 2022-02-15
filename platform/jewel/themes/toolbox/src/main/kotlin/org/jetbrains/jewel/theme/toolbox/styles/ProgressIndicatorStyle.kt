package org.jetbrains.jewel.theme.toolbox.styles

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ShapeStroke
import org.jetbrains.jewel.styles.ControlStyle
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.styles.localNotProvided
import org.jetbrains.jewel.theme.toolbox.Palette
import org.jetbrains.jewel.theme.toolbox.ToolboxMetrics
import org.jetbrains.jewel.theme.toolbox.components.ProgressIndicatorState
import org.jetbrains.jewel.theme.toolbox.components.drawRectangleProgress
import org.jetbrains.jewel.theme.toolbox.components.drawRoundedRectangleProgress

typealias ProgressIndicatorStyle = ControlStyle<ProgressIndicatorAppearance, ProgressIndicatorState>

typealias ProgressIndicatorPainter = DrawScope.(start: Float, end: Float, appearance: ProgressIndicatorAppearance) -> Unit

data class ProgressIndicatorAppearance(
    val textStyle: TextStyle = TextStyle.Default,
    val color: Color = Color.White,
    val backgroundColor: Color = Color.White.copy(alpha = 0.24f),
    val shapeStroke: ShapeStroke? = null,
    val shape: Shape = RectangleShape,

    val progressPadding: PaddingValues = PaddingValues(2.dp),
    val painter: ProgressIndicatorPainter = DrawScope::drawRectangleProgress,

    val minWidth: Dp,
    val minHeight: Dp,
)

val LocalProgressIndicatorStyle = compositionLocalOf<ProgressIndicatorStyle> { localNotProvided() }
val Styles.progressIndicator: ProgressIndicatorStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalProgressIndicatorStyle.current

fun ProgressIndicatorStyle(palette: Palette, metrics: ToolboxMetrics) = ProgressIndicatorStyle {
    default {
        state(
            ProgressIndicatorState.Normal, ProgressIndicatorAppearance(
            color = palette.controlContent,
            shape = RoundedCornerShape(metrics.cornerSize),
            backgroundColor = palette.controlBackground,
            progressPadding = PaddingValues(metrics.adornmentsThickness),
            painter = { start, end, appearance ->
                drawRoundedRectangleProgress(
                    start,
                    end,
                    appearance,
                    metrics.cornerSize
                )
            },
            minWidth = metrics.base * 4,
            minHeight = metrics.base * 2,
        )
        )
    }
}
