package org.jetbrains.jewel.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.unit.Dp

/**
 * Describes how a component's stroke (border) is drawn. Use [Stroke.None] for no stroke, [Stroke.Solid] for a
 * single-color stroke, or [Stroke.Brush] for a gradient stroke.
 */
@Suppress("AbstractClassCanBeInterface") // Binary compatibility: sealed class cannot be changed to interface
public sealed class Stroke {
    /** No stroke; the component has no visible border. */
    @Immutable
    public object None : Stroke() {
        override fun toString(): String = "None"
    }

    /** A stroke drawn with a single solid [color]. */
    @Immutable
    @GenerateDataFunctions
    public class Solid
    internal constructor(
        /** The width of the stroke. */
        public val width: Dp,
        /** The solid color of the stroke. */
        public val color: Color,
        /** Where the stroke is drawn relative to the component bounds. */
        public val alignment: Alignment,
        /** The amount by which to expand the stroke beyond the component bounds. */
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

    /** A stroke drawn with a [Brush][androidx.compose.ui.graphics.Brush], allowing gradient effects. */
    @Immutable
    @GenerateDataFunctions
    public class Brush
    internal constructor(
        /** The width of the stroke. */
        public val width: Dp,
        /** The brush used to paint the stroke. */
        public val brush: androidx.compose.ui.graphics.Brush,
        /** Where the stroke is drawn relative to the component bounds. */
        public val alignment: Alignment,
        /** The amount by which to expand the stroke beyond the component bounds. */
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

    /** Controls where the stroke is drawn relative to the component's bounds. */
    public enum class Alignment {
        /** The stroke is drawn inside the component bounds. */
        Inside,
        /** The stroke is centered on the component bounds edge. */
        Center,
        /** The stroke is drawn outside the component bounds. */
        Outside,
    }
}

/**
 * Creates a [Stroke] from a solid [color]. Returns [Stroke.None] if [width] is zero or [color] is
 * [Color.Unspecified][androidx.compose.ui.graphics.Color.Unspecified].
 *
 * @param width The width of the stroke.
 * @param color The solid color of the stroke.
 * @param alignment Where the stroke is drawn relative to the component bounds.
 * @param expand Optional amount by which to expand the stroke beyond the component bounds.
 */
public fun Stroke(width: Dp, color: Color, alignment: Stroke.Alignment, expand: Dp = Dp.Unspecified): Stroke {
    if (width.value == 0f) return Stroke.None
    if (color.isUnspecified) return Stroke.None

    return Stroke.Solid(width, color, alignment, expand)
}

/**
 * Creates a [Stroke] from a [Brush]. Returns [Stroke.None] if [width] is zero or the brush is a
 * [SolidColor][androidx.compose.ui.graphics.SolidColor] with an unspecified color.
 *
 * @param width The width of the stroke.
 * @param brush The brush used to paint the stroke.
 * @param alignment Where the stroke is drawn relative to the component bounds.
 * @param expand Optional amount by which to expand the stroke beyond the component bounds.
 */
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
