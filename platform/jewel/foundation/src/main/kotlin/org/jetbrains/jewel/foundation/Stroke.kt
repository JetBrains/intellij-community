package org.jetbrains.jewel.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.unit.Dp

sealed class Stroke {
    @Immutable
    object None : Stroke() {

        override fun toString(): String = "None"
    }

    @Immutable
    data class Solid internal constructor(
        val width: Dp,
        val color: Color,
        val alignment: Alignment,
        val expand: Dp
    ) : Stroke()

    @Immutable
    data class Brush internal constructor(
        val width: Dp,
        val brush: androidx.compose.ui.graphics.Brush,
        val alignment: Alignment,
        val expand: Dp
    ) : Stroke()

    enum class Alignment {
        Inside, Center, Outside
    }
}

fun Stroke(width: Dp, color: Color, alignment: Stroke.Alignment, expand: Dp = Dp.Unspecified): Stroke {
    if (width.value == 0f) return Stroke.None
    if (color.isUnspecified) return Stroke.None

    return Stroke.Solid(width, color, alignment, expand)
}

fun Stroke(width: Dp, brush: Brush, alignment: Stroke.Alignment, expand: Dp = Dp.Unspecified): Stroke {
    if (width.value == 0f) return Stroke.None
    return when (brush) {
        is SolidColor -> {
            if (brush.value.isUnspecified) {
                Stroke.None
            } else {
                Stroke.Solid(width, brush.value, alignment, expand)
            }
        }

        else -> Stroke.Brush(width, brush, alignment, expand)
    }
}
