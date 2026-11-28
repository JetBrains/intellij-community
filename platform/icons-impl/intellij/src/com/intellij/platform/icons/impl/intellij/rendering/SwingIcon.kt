// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.openapi.application.EDT
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.impl.rendering.ResolvedScalingContext
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.impl.rendering.resolve
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.scale.FactorScale
import com.intellij.platform.icons.scale.FitAreaScale
import com.intellij.platform.icons.scale.IconScale
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.scale.fitArea
import com.intellij.platform.icons.swing.ScalableSwingIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.util.WeakHashMap
import javax.swing.Icon
import kotlin.math.roundToInt

class SwingIcon(
  val iconDescriptor: IconDescriptor,
  val scale: IconScale? = null
): Icon, ScalableSwingIcon {
  internal val users = WeakHashMap<Component, Boolean>()

  private val renderer by lazy {
    val flow = IconRendererManager.createUpdateFlow(null) {
      withContext(Dispatchers.EDT) {
        for (user in users.keys) {
          user.repaint()
        }
      }
    }
    val context = IconRendererManager.createRenderingContext(flow)
    IconRendererManager.getInstance().createRenderer(iconDescriptor, context)
  }

  private val dimensions: Dimensions
    get() = renderer.resolve(1f, scale).finalDimensions

  override fun scaled(scale: IconScale): ScalableSwingIcon {
    val mergedScale = if (this.scale != null) {
      when (scale) {
        is FactorScale if this.scale is FactorScale -> {
          factor(scale.factor * this.scale.factor)
        }
        is FactorScale if this.scale is FitAreaScale -> {
          fitArea(this.scale.width * scale.factor, this.scale.height * scale.factor, this.scale.relative)
        }
        else -> {
          scale
        }
      }
    } else {
      scale
    }
    return SwingIcon(iconDescriptor, mergedScale)
  }

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    if (c != null) {
      users.putIfAbsent(c, true)
    }
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
    // nothing to paint, and BufferedImage rejects an empty size (e.g. an icon whose image is not available)
    if (w <= 0 || h <= 0) return
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val sublayer = img.createGraphics()
    try {
      painting(sublayer as Graphics2D)
      val g2d = g as Graphics2D
      val baseScale = scaling.context.displayDensity.toDouble()
      g2d.scale(1.0 / baseScale, 1.0 / baseScale)
      val scaledX = (x * baseScale).roundToInt()
      val scaledY = (y * baseScale).roundToInt()
      g.drawImage(img, scaledX, scaledY, scaledX + w, scaledY + h, 0, 0, img.width, img.height, null)
      g2d.scale(baseScale, baseScale)
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

  /**
   * Icons of the same descriptor and scale render the same, so they are equal. For example, `Presentation.getIcon` creates
   * an icon on each call, and its callers compare the icons of two calls. The identity scale `factor(1)` is equal to no scale.
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SwingIcon) return false
    return iconDescriptor == other.iconDescriptor && effectiveScale() == other.effectiveScale()
  }

  override fun hashCode(): Int = 31 * iconDescriptor.hashCode() + (effectiveScale()?.hashCode() ?: 0)

  private fun effectiveScale(): IconScale? = scale?.takeUnless { it is FactorScale && it.factor == 1.0 }

  /**
   * Shows the descriptor, with its layers and image paths, for example in the UI Inspector, the debugger and logs.
   * It does not load or measure the icon.
   */
  override fun toString(): String = if (scale == null) "SwingIcon($iconDescriptor)" else "SwingIcon($iconDescriptor, scale=$scale)"
}
