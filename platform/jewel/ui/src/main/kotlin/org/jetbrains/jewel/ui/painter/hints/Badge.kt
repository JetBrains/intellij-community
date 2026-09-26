package org.jetbrains.jewel.ui.painter.hints

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.painter.BadgePainter
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterWrapperHint
import org.jetbrains.jewel.ui.painter.badge.BadgeShape
import org.jetbrains.jewel.ui.painter.badge.DotBadgeShape

@GenerateDataFunctions
private class BadgeImpl(private val color: Color, private val shape: BadgeShape) : PainterWrapperHint {
    override fun PainterProviderScope.wrap(painter: Painter): Painter = BadgePainter(painter, color, shape)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BadgeImpl

        if (color != other.color) return false
        if (shape != other.shape) return false

        return true
    }

    override fun hashCode(): Int {
        var result = color.hashCode()
        result = 31 * result + shape.hashCode()
        return result
    }

    override fun toString(): String = "BadgeImpl(color=$color, shape=$shape)"
}

/** Adds a colored badge to the image being loaded. */
@Suppress("FunctionName")
public fun Badge(color: Color, shape: BadgeShape = DotBadgeShape.Default): PainterHint =
    if (color.isSpecified) BadgeImpl(color, shape) else PainterHint.None
