package org.jetbrains.jewel.ui.painter.badge

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * A shape used to draw badges. Badge shapes have a clear area surrounding them, whose outline is determined by
 * [createHoleOutline].
 *
 * @see org.jetbrains.jewel.ui.painter.hints.Badge
 * @see org.jetbrains.jewel.ui.painter.BadgePainter
 */
@Immutable
public interface BadgeShape : Shape {
    /** Create the outline of the clear area (or "hole") surrounding this badge shape. */
    public fun createHoleOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline
}

internal val emptyOutline = Outline.Rectangle(Rect.Zero)
