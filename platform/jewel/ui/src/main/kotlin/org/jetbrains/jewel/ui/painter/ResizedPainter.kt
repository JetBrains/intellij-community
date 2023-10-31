package org.jetbrains.jewel.ui.painter

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

class ResizedPainter(private val delegate: Painter, private val size: Size) : DelegatePainter(delegate) {

    override val intrinsicSize: Size
        get() = size

    override fun DrawScope.onDraw() {
        drawDelegate()
    }
}
