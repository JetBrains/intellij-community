package org.jetbrains.jewel.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.unit.Dp

public sealed class Stroke {
    @Immutable
    public object None : Stroke() {
        override fun toString(): String = "None"
    }

    @Immutable
    @GenerateDataFunctions
    public class Solid
    internal constructor(
        public val width: Dp,
        public val color: Color,
        public val alignment: Alignment,
        public val expand: Dp,
    ) : Stroke()

    @Immutable
    @GenerateDataFunctions
    public class Brush
    internal constructor(
        public val width: Dp,
        public val brush: androidx.compose.ui.graphics.Brush,
        public val alignment: Alignment,
        public val expand: Dp,
    ) : Stroke()

    public enum class Alignment {
        Inside,
        Center,
        Outside,
    }
}

public fun Stroke(width: Dp, color: Color, alignment: Stroke.Alignment, expand: Dp = Dp.Unspecified): Stroke {
    if (width.value == 0f) return Stroke.None
    if (color.isUnspecified) return Stroke.None

    return Stroke.Solid(width, color, alignment, expand)
}

public fun Stroke(width: Dp, brush: Brush, alignment: Stroke.Alignment, expand: Dp = Dp.Unspecified): Stroke {
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
