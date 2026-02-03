package org.jetbrains.jewel.ui.painter

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.unit.Density
import org.jetbrains.jewel.ui.painter.badge.BadgeShape

/**
 * Paints a badge over the [source].
 *
 * An area corresponding to the result of [BadgeShape.createHoleOutline] is cleared out first, to allow for visual
 * separation with the badge,and then the [BadgeShape.createOutline] is filled with the [color].
 */
public class BadgePainter(private val source: Painter, private val color: Color, private val shape: BadgeShape) :
    DelegatePainter(source) {
    /**
     * Optional [Paint] used to draw contents into an offscreen layer to apply alpha or [ColorFilter] parameters
     * accordingly. If no alpha or [ColorFilter] is provided or the [Painter] implementation implements [applyAlpha] and
     * [applyColorFilter] then this paint is not used.
     */
    private var layerPaint: Paint? = null

    /** Lazily create a [Paint] object or return the existing instance if it is already allocated. */
    private fun obtainPaint(): Paint {
        var target = layerPaint
        if (target == null) {
            target = Paint()
            layerPaint = target
        }
        return target
    }

    private fun DrawScope.drawHole() {
        val badge = shape.createHoleOutline(size, layoutDirection, Density(density))
        drawOutline(badge, Color.White, alpha, blendMode = BlendMode.Clear)
    }

    private fun DrawScope.drawBadge() {
        val badge = shape.createOutline(size, layoutDirection, Density(density))
        drawOutline(badge, color, alpha)
    }

    override fun DrawScope.onDraw() {
        val layerRect = Rect(Offset.Zero, Size(size.width, size.height))
        drawIntoCanvas { canvas ->
            canvas.withSaveLayer(layerRect, obtainPaint()) {
                drawDelegate()
                drawHole()
                drawBadge()
            }
        }
    }
}
