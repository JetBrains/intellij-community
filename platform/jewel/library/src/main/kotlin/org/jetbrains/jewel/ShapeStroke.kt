package org.jetbrains.jewel

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

sealed class ShapeStroke<T : Brush> {

    abstract val width: Dp
    abstract val brush: T
    abstract val insets: Insets

    @Immutable
    data class SolidColor(
        override val width: Dp,
        val color: Color,
        override val insets: Insets = Insets(width / 2)
    ) : ShapeStroke<androidx.compose.ui.graphics.SolidColor>() {

        override val brush = SolidColor(color)
    }

    @Immutable
    data class Brush(
        override val width: Dp,
        override val brush: androidx.compose.ui.graphics.Brush,
        override val insets: Insets = Insets(width / 2)
    ) : ShapeStroke<androidx.compose.ui.graphics.Brush>()

    companion object {

    }
}

@Composable
inline fun <S> Transition<S>.animateShapeStroke(
    label: String = "ShapeStrokeAnimation",
    targetValueByState: @Composable (state: S) -> ShapeStroke<*>?
): State<ShapeStroke<*>> =
    when (targetValueByState(targetState)) {
        is ShapeStroke.SolidColor -> animateSolidColorShapeStroke(label) { targetValueByState(it) as ShapeStroke.SolidColor }
        is ShapeStroke.Brush -> animateBrushShapeStroke(label) { targetValueByState(it) as ShapeStroke.Brush }
        else -> error("")
    }

@Composable
inline fun <S> Transition<S>.animateSolidColorShapeStroke(
    label: String = "ShapeStrokeAnimation",
    targetValueByState: @Composable (state: S) -> ShapeStroke.SolidColor?
): State<ShapeStroke.SolidColor> {
    val targetValue = targetValueByState(targetState)

    val width by animateDp(label = "$label.width") { targetValue?.width ?: 0.dp }
    val color by animateColor(label = "$label.color") { targetValue?.color ?: Color.Unspecified }
    val insets by animateInsets(label = "$label.insets") { targetValue?.insets ?: Insets.Empty }

    return derivedStateOf { ShapeStroke.SolidColor(width, color, insets) }
}

@Composable
inline fun <S> Transition<S>.animateBrushShapeStroke(
    label: String = "ShapeStrokeAnimation",
    targetValueByState: @Composable (state: S) -> ShapeStroke.Brush?
): State<ShapeStroke.Brush> {
    val targetValue = targetValueByState(targetState)

    val width by animateDp(label = "$label.width") { targetValue?.width ?: 0.dp }
    val brush = targetValue?.brush ?: Color.Unspecified.toBrush()
    val insets by animateInsets(label = "$label.insets") { targetValue?.insets ?: Insets.Empty }

    return derivedStateOf { ShapeStroke.Brush(width, brush, insets) }
}
