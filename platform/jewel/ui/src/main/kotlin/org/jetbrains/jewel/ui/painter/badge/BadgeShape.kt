package org.jetbrains.jewel.ui.painter.badge

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

@Immutable
public interface BadgeShape : Shape {

    public fun createHoleOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline
}

internal val emptyOutline = Outline.Rectangle(Rect.Zero)
