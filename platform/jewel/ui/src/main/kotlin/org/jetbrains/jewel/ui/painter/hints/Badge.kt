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
}

/** Adds a colored badge to the image being loaded. */
@Suppress("FunctionName")
public fun Badge(color: Color, shape: BadgeShape = DotBadgeShape.Default): PainterHint =
    if (color.isSpecified) BadgeImpl(color, shape) else PainterHint.None
