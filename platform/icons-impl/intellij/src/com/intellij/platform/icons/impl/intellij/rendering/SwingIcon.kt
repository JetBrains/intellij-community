// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.platform.icons.Icon
import com.intellij.platform.icons.impl.rendering.ResolvedScalingContext
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.RenderingContext
import com.intellij.platform.icons.impl.rendering.resolve
import com.intellij.platform.icons.scale.IconScale
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage

class SwingIcon(
  val icon: Icon,
  val scale: IconScale? = null
): javax.swing.Icon {
  private val renderer by lazy {
    IconRendererManager.getInstance().createRenderer(icon, RenderingContext.Empty) // TODO listen for updates to redraw Icon
  }
  private val dimensions by lazy {
    renderer.resolve(1f, scale).finalDimensions
  }

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val scaling = renderer.resolve(g.getDensity(), scale)
    withLayer(scaling, g, x, y) { newGraphics ->
      val swingApi = SwingLayerPaintingContext(
        c,
        newGraphics,
        0,
        0,
        scaling = scaling.context
      )
      renderer.render(swingApi)
    }
  }

  @Suppress("UndesirableClassUsage")
  private fun withLayer(scaling: ResolvedScalingContext, g: Graphics, x: Int, y: Int, painting: (Graphics2D) -> Unit) {
    val w = scaling.finalDimensions.width
    val h = scaling.finalDimensions.height
    if (w < 0 || h < 0) return
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val sublayer = img.createGraphics()
    try {
      painting(sublayer as Graphics2D)
      val g2d = g as Graphics2D
      g2d.scale(1.0 / scaling.context.displayDensity, 1.0 / scaling.context.displayDensity)
      g.drawImage(img, x, y, w, h, 0, 0, img.width, img.height, null)
      g2d.scale(scaling.context.displayDensity.toDouble(), scaling.context.displayDensity.toDouble())
    } finally {
      sublayer.dispose()
    }
  }

  private fun Graphics?.getDensity(): Float {
    return if (this is Graphics2D) {
      transform.scaleX.toFloat()
    } else 1f
  }

  override fun getIconWidth(): Int {
    return dimensions.width
  }

  override fun getIconHeight(): Int {
    return dimensions.height
  }
}