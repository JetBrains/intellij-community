package org.jetbrains.jewel.ui.painter.badge

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.jewel.foundation.GenerateDataFunctions

/** @see com.intellij.ui.BadgeDotProvider */
@Immutable
@GenerateDataFunctions
public class DotBadgeShape(
    public val x: Float = 16.5f / 20,
    public val y: Float = 3.5f / 20,
    public val radius: Float = 3.5f / 20,
    public val border: Float = 1.5f / 20,
) : BadgeShape {
    override fun createHoleOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        createOutline(size, hole = true)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        createOutline(size, hole = false)

    private fun createOutline(size: Size, hole: Boolean): Outline {
        val dotSize = size.width.coerceAtMost(size.height)

        if (dotSize <= 0) return emptyOutline

        val radius = dotSize * radius
        if (radius <= 0) return emptyOutline

        val x = size.width * x
        if (0 > x + radius || x - radius > size.width) return emptyOutline

        val y = size.height * y
        if (0 > y + radius || y - radius > size.height) return emptyOutline

        val border =
            when {
                hole -> dotSize * border
                else -> 0.0f
            }

        val r = radius + border.coerceAtLeast(0.0f)

        return Outline.Rounded(
            RoundRect(left = x - r, top = y - r, right = x + r, bottom = y + r, cornerRadius = CornerRadius(r))
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DotBadgeShape

        if (x != other.x) return false
        if (y != other.y) return false
        if (radius != other.radius) return false
        if (border != other.border) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + radius.hashCode()
        result = 31 * result + border.hashCode()
        return result
    }

    override fun toString(): String = "DotBadgeShape(x=$x, y=$y, radius=$radius, border=$border)"

    public companion object {
        public val Default: DotBadgeShape = DotBadgeShape()
    }
}
