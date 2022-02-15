package org.jetbrains.jewel

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

@Immutable
data class ShapeStroke(val width: Dp, val brush: Brush, val insets: Insets = Insets(width / 2))

@Composable
inline fun <S> Transition<S>.animateShapeStroke(
    label: String = "ShapeStrokeAnimation",
    targetValueByState: @Composable (state: S) -> ShapeStroke?
): State<ShapeStroke> {

    val width by animateDp(label = "$label.width") { targetValueByState(it)?.width ?: 0.dp }
    // TODO val color by animateColor(label = "$label.color") { targetValueByState(it)?.color ?: Color.Unspecified }
    val insets by animateInsets(label = "$label.insets") { targetValueByState(it)?.insets ?: Insets.Empty }

    return derivedStateOf { ShapeStroke(width, SolidColor(Color.Red), insets) }
}
