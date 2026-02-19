package org.jetbrains.jewel.ui.painter

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.LayoutDirection

/**
 * A painter that delegates drawing to another [Painter], but can apply custom alphas, filters and layoutDirection to
 * it.
 */
public open class DelegatePainter(private val delegate: Painter) : Painter() {
    override val intrinsicSize: Size
        get() = delegate.intrinsicSize

    protected var alpha: Float = 1f

    protected var filter: ColorFilter? = null

    protected var layoutDirection: LayoutDirection = LayoutDirection.Ltr

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.filter = colorFilter
        return true
    }

    override fun applyLayoutDirection(layoutDirection: LayoutDirection): Boolean {
        this.layoutDirection = layoutDirection
        return true
    }

    protected fun DrawScope.drawDelegate() {
        with(delegate) { draw(this@drawDelegate.size, alpha, filter) }
    }

    override fun DrawScope.onDraw() {
        drawDelegate()
    }
}
