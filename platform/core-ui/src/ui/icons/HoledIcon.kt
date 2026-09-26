// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.openapi.util.ScalableIcon
import com.intellij.util.ui.JBScalableIcon
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Path2D
import javax.swing.Icon

abstract class HoledIcon(val icon: Icon) : JBScalableIcon(), ReplaceableIcon {
  protected abstract fun copyWith(icon: Icon):Icon

  protected abstract fun createHole(width: Int, height: Int): Shape?
  protected abstract fun paintHole(g: Graphics2D, width: Int, height: Int)

  override fun getScale(): Float = (icon as? ScalableIcon)?.scale ?: 1f
  override fun scale(factor: Float): Icon = copyWith(scaleIconOrLoadCustomVersion(icon = icon, scale = factor))

  private val baseIconBounds: Rectangle
    get() = Rectangle(0, 0, icon.iconWidth, icon.iconHeight)

  private val combinedBounds: Rectangle
    get() {
      val iconBounds = baseIconBounds
      val holeBounds = createHole(iconBounds.width, iconBounds.height)?.bounds ?: return iconBounds
      return iconBounds.union(holeBounds)
    }

  /**
   * Returns the extra size added by the hole, relative to the base icon.
   *
   * When the hole is fully inside the base icon, it returns zero insets.
   * Otherwise, the returned insets indicate how much the hole "protrudes" outside and at which sides.
   * For example, a typical colored badge usually extends a bit upwards, so the top returned inset will be non-zero.
   *
   * The returned values take scaling into account and correspond to the actual geometry used when painting the icon.
   */
  fun getExtraInsets(): Insets {
    val combinedBounds = combinedBounds
    @Suppress("UseDPIAwareInsets") // everything is already scaled
    return Insets(
      (baseIconBounds.y - combinedBounds.y).coerceAtLeast(0),
      (baseIconBounds.x - combinedBounds.x).coerceAtLeast(0),
      ((combinedBounds.y + combinedBounds.height) - (baseIconBounds.y + baseIconBounds.height)).coerceAtLeast(0),
      ((combinedBounds.x + combinedBounds.width) - (baseIconBounds.x + baseIconBounds.width)).coerceAtLeast(0),
    )
  }

  override fun getIconWidth(): Int = combinedBounds.width
  override fun getIconHeight(): Int = combinedBounds.height

  override fun paintIcon(c: Component?, graphics: Graphics, x: Int, y: Int) {
    val bounds = combinedBounds
    val baseIconBounds = baseIconBounds
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
