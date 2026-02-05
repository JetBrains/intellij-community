// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering

import org.jetbrains.icons.Icon
import org.jetbrains.icons.rendering.IconRendererManager
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.icons.impl.rendering.layers.applyTo
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage

class SwingIcon(
  val icon: Icon
): javax.swing.Icon {
  private val renderer by lazy {
    IconRendererManager.getInstance().createRenderer(icon, RenderingContext.Empty) // TODO listen for updates to redraw Icon
  }
  private val dimensions by lazy {
    renderer.calculateExpectedDimensions(SwingScalingContext(1f))
  }

  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    val scaling = getScaling(g)
    withLayer(scaling, c, g, x, y) { newGraphics ->
      val swingApi = SwingPaintingApi(c, newGraphics, 0, 0, scaling = scaling)
      renderer.render(swingApi)
    }
  }

  private fun withLayer(scaling: ScalingContext, c: Component, g: Graphics, x: Int, y: Int, painting: (Graphics2D) -> Unit) {
    val w = scaling.applyTo(c.width - x)
    val h = scaling.applyTo(c.height - y)
    if (w < 0 || h < 0) return
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val sublayer = img.createGraphics()
    try {
      painting(sublayer as Graphics2D)
      g.drawImage(img, x, y, c.width, c.height, 0, 0, img.width, img.height, null)
    } finally {
      sublayer.dispose()
    }
  }

  private fun Graphics2D.clearTransform() {
    scale(1 / transform.scaleX, 1 / transform.scaleY)
  }

  private fun getScaling(g: Graphics?): SwingScalingContext {
    if (g is Graphics2D) {
      return SwingScalingContext(g.transform.scaleX.toFloat())
    } else return SwingScalingContext(1f)
  }

  override fun getIconWidth(): Int {
    return dimensions.width
  }

  override fun getIconHeight(): Int {
    return dimensions.height
  }
}