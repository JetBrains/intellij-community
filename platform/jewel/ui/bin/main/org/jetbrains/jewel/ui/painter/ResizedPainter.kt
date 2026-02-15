package org.jetbrains.jewel.ui.painter

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

/** A delegate painter that overrides the intrinsic size of the [delegate]. */
public class ResizedPainter(delegate: Painter, private val size: Size) : DelegatePainter(delegate) {
    override val intrinsicSize: Size
        get() = size

    override fun DrawScope.onDraw() {
        drawDelegate()
    }
}
