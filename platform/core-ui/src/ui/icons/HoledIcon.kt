// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.openapi.util.ScalableIcon
import com.intellij.util.ui.JBScalableIcon
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.geom.Path2D
import javax.swing.Icon

@ApiStatus.Internal
@ApiStatus.Experimental
abstract class HoledIcon(protected val icon: Icon) : JBScalableIcon(), ReplaceableIcon {
  protected abstract fun copyWith(icon: Icon):Icon

  protected abstract fun createHole(width: Int, height: Int): Shape?
  protected abstract fun paintHole(g: Graphics2D, width: Int, height: Int)

  override fun getScale() = (icon as? ScalableIcon)?.scale ?: 1f
  override fun scale(factor: Float) = copyWith(scaleIconOrLoadCustomVersion(icon = icon, scale = factor))

  override fun getIconWidth() = icon.iconWidth
  override fun getIconHeight() = icon.iconHeight
  override fun paintIcon(c: Component?, graphics: Graphics, x: Int, y: Int) {
    val width = iconWidth
    val height = iconHeight
    val g = graphics.create(x, y, width, height)
    try {
      val hole = createHole(width, height)
      if (hole != null) {
        val area = g.clip
        if (g is Graphics2D) {
          g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
          g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
          g.clip = hole
          paintHole(g, width, height)
          g.clip(hole) // do not allow painting outside the hole
        }
        // subtract hole from old clip
        val path = Path2D.Float(Path2D.WIND_EVEN_ODD)
        path.append(area, false)
        path.append(hole, false)
        g.clip = path // safe because based on old clip
      }
      icon.paintIcon(c, g, 0, 0)
    }
    finally {
      g.dispose()
    }
  }
}
