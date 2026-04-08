// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.openapi.util.ScalableIcon
import com.intellij.util.ui.JBScalableIcon
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Path2D
import javax.swing.Icon
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
@ApiStatus.Experimental
abstract class HoledIcon(val icon: Icon) : JBScalableIcon(), ReplaceableIcon {
  protected abstract fun copyWith(icon: Icon):Icon

  protected abstract fun createHole(width: Int, height: Int): Shape?
  protected abstract fun paintHole(g: Graphics2D, width: Int, height: Int)

  override fun getScale() = (icon as? ScalableIcon)?.scale ?: 1f
  override fun scale(factor: Float) = copyWith(scaleIconOrLoadCustomVersion(icon = icon, scale = factor))

  private val combinedBounds: Rectangle
    get() {
      val iconBounds = Rectangle(0, 0, icon.iconWidth, icon.iconHeight)
      val holeBounds = createHole(iconBounds.width, iconBounds.height)?.bounds ?: return iconBounds
      val minX = min(iconBounds.x, holeBounds.x)
      val minY = min(iconBounds.y, holeBounds.y)
      val maxX = max(iconBounds.x + iconBounds.width, holeBounds.x + holeBounds.width)
      val maxY = max(iconBounds.y + iconBounds.height, holeBounds.y + holeBounds.height)
      return Rectangle(minX, minY, maxX - minX, maxY - minY)
    }

  override fun getIconWidth() = combinedBounds.width
  override fun getIconHeight() = combinedBounds.height

  override fun paintIcon(c: Component?, graphics: Graphics, x: Int, y: Int) {
    val bounds = combinedBounds
    val baseIconBounds = Rectangle(0, 0, icon.iconWidth, icon.iconHeight)
    // (x,y) corresponds to the combined icon position (because that's what we're painting).
    // (bounds.x, bounds.y) corresponds to the combined icon position relative to the base icon (because that's how it's computed).
    val g = graphics.create(x, y, bounds.width, bounds.height)
    // Now, (0,0) corresponds to (x,y).
    // Therefore, we must translate so that we're painting relative to the base icon, as that is what createHole() expects.
    g.translate(-bounds.x, -bounds.y)
    try {
      val hole = createHole(baseIconBounds.width, baseIconBounds.height)
      if (hole != null) {
        val area = g.clip // save now before clipping to paint the hole itself
        if (g is Graphics2D) {
          g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
          g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
          g.clip(hole) // do not allow painting outside the hole
          paintHole(g, baseIconBounds.width, baseIconBounds.height)
        }
        // subtract hole from old clip
        val path = Path2D.Float(Path2D.WIND_EVEN_ODD)
        path.append(area, false)
        path.append(hole, false)
        // This should be safe because the new clip is based on the old one.
        g.clip = path
        // But in practice it doesn't always work for edge cases, like when the hole touches the outer rectangle, so clip again.
        (g as Graphics2D?)?.clip(area)
      }
      icon.paintIcon(c, g, 0, 0)
    }
    finally {
      g.dispose()
    }
  }
}
