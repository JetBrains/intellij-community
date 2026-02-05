// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.px
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.impl.intellij.rendering.images.awtImage
import com.intellij.platform.icons.impl.rendering.DefaultScalingContext
import com.intellij.platform.icons.rendering.BitmapImageResource
import com.intellij.platform.icons.rendering.DrawMode
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.LayerPaintingContext
import com.intellij.platform.icons.rendering.RescalableImageResource
import com.intellij.platform.icons.rendering.ScalingContext
import com.intellij.platform.icons.scale.fitArea
import com.intellij.platform.icons.swing.toAwtColor
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D

class SwingLayerPaintingContext(
  val c: Component?,
  val g: Graphics,
  val x: Int,
  val y: Int,
  private val customX: Int? = null,
  private val customY: Int? = null,
  override val slotHeight: Int? = null,
  override val slotWidth: Int? = null,
  private val overrideColorFilter: ColorFilter? = null,
  override val scaling: ScalingContext = DefaultScalingContext(1f, 1f),
) : LayerPaintingContext {
  override val offsetX: Int = customX ?: x
  override val offsetY: Int = customY ?: y

  override fun createNestedLayer(
    x: Int?,
    y: Int?,
    slotWidth: Int?,
    slotHeight: Int?,
    scale: Float,
    overrideColorFilter: ColorFilter?
  ): LayerPaintingContext {
    return SwingLayerPaintingContext(
        c,
        g,
        this.x,
        this.y,
        x ?: customX,
        y ?: customY,
        slotHeight,
        slotWidth,
        overrideColorFilter ?: this.overrideColorFilter,
        DefaultScalingContext(scaling.displayDensity, scaling.contextScale * scale)
    )
  }

  override fun drawCircle(color: Color, x: Int, y: Int, radius: Float, alpha: Float, mode: DrawMode) {
    val r = radius.toDouble()
    drawShape(color, Ellipse2D.Double(x - r, y - r, r + r, r + r), alpha, mode)
  }

  override fun drawRect(color: Color, x: Int, y: Int, width: Int, height: Int, alpha: Float, mode: DrawMode) {
    drawShape(color, Rectangle2D.Double(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble()), alpha, mode)
  }

  @Suppress("unused")
  private fun drawShape(color: Color, shape: Shape, alpha: Float, mode: DrawMode) {
    setDrawingDefaults()
    if (g is Graphics2D) {
      val oldComposite = g.composite
      val oldPaint = g.paint
      try {
        if (mode == DrawMode.Clear) {
          g.composite = AlphaComposite.Clear
        }
        g.color = color.toAwtColor()
        if (mode == DrawMode.Stroke) {
          g.draw(shape)
        }
        else {
          g.fill(shape)
        }
      }
      finally {
        g.composite = oldComposite
        g.paint = oldPaint
      }
    }
  }

  override fun drawImage(
    image: ImageResource,
    x: Int,
    y: Int,
    width: Int?,
    height: Int?,
    srcX: Int,
    srcY: Int,
    srcWidth: Int?,
    srcHeight: Int?,
    alpha: Float,
    colorFilter: ColorFilter?,
  ) {
    when (image) {
      is BitmapImageResource -> {
        drawImage(image, x, y, width, height, srcX, srcY, srcWidth, srcHeight, alpha, colorFilter)
      }
      is RescalableImageResource -> {
        drawImage(image, x, y, width, height, srcX, srcY, srcWidth, srcHeight, alpha, colorFilter)
      }
    }
  }

  private fun setDrawingDefaults() {
    if (g !is Graphics2D) return
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
  }

  private fun drawImage(
    image: RescalableImageResource,
    x: Int,
    y: Int,
    width: Int?,
    height: Int?,
    srcX: Int,
    srcY: Int,
    srcWidth: Int?,
    srcHeight: Int?,
    alpha: Float,
    colorFilter: ColorFilter?,
  ) {
    val realWidth = (width ?: image.width)
    val realHeight = (height ?: image.height)
    if (realWidth == null || realHeight == null) return
    val swingImage = image.scale(scaling.displayDensity, fitArea(realWidth.px, realHeight.px))
    drawImage(swingImage, x, y, width, height, srcX, srcY, srcWidth, srcHeight, alpha, colorFilter)
  }

  private fun drawImage(
    image: BitmapImageResource,
    x: Int,
    y: Int,
    width: Int?,
    height: Int?,
    srcX: Int,
    srcY: Int,
    srcWidth: Int?,
    srcHeight: Int?,
    alpha: Float,
    colorFilter: ColorFilter?,
  ) {
    val swingImage = image.awtImage()

    drawImage(
      swingImage,
      x,
      y,
      width,
      height,
      srcX,
      srcY,
      srcWidth,
      srcHeight,
      alpha,
      colorFilter
    )
  }

  @Suppress("unused")
  private fun drawImage(
    image: Image,
    x: Int,
    y: Int,
    width: Int?,
    height: Int?,
    srcX: Int,
    srcY: Int,
    srcWidth: Int?,
    srcHeight: Int?,
    alpha: Float,
    colorFilter: ColorFilter?,
  ) {
    // TODO apply alpha & color filters

    val imageWidth = image.getWidth(null)
    val imageHeight = image.getHeight(null)

    if (imageWidth == 0 || imageHeight == 0) return
    setDrawingDefaults()
    g.drawImage(
      image,
      x,
      y,
      x + (width ?: imageWidth),
      y + (height ?: imageHeight),
      srcX,
      srcY,
      srcX + (srcWidth ?: imageWidth),
      srcY + (srcHeight ?: imageHeight),
      null,
    )
  }
}