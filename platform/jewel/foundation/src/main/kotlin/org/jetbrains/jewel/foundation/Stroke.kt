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
    ) : Stroke() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Solid

            if (width != other.width) return false
            if (color != other.color) return false
            if (alignment != other.alignment) return false
            if (expand != other.expand) return false

            return true
        }

        override fun hashCode(): Int {
            var result = width.hashCode()
            result = 31 * result + color.hashCode()
            result = 31 * result + alignment.hashCode()
            result = 31 * result + expand.hashCode()
            return result
        }

        override fun toString(): String = "Solid(width=$width, color=$color, alignment=$alignment, expand=$expand)"
    }

    @Immutable
    @GenerateDataFunctions
    public class Brush
    internal constructor(
        public val width: Dp,
        public val brush: androidx.compose.ui.graphics.Brush,
        public val alignment: Alignment,
        public val expand: Dp,
    ) : Stroke() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Brush

            if (width != other.width) return false
            if (brush != other.brush) return false
            if (alignment != other.alignment) return false
            if (expand != other.expand) return false

            return true
        }

        override fun hashCode(): Int {
            var result = width.hashCode()
            result = 31 * result + brush.hashCode()
            result = 31 * result + alignment.hashCode()
            result = 31 * result + expand.hashCode()
            return result
        }

        override fun toString(): String = "Brush(width=$width, brush=$brush, alignment=$alignment, expand=$expand)"
    }

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
