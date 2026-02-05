// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering

import org.jetbrains.icons.design.Color
import org.jetbrains.icons.filters.ColorFilter
import org.jetbrains.icons.rendering.BitmapImageResource
import org.jetbrains.icons.rendering.Bounds
import org.jetbrains.icons.rendering.DrawMode
import org.jetbrains.icons.rendering.FitAreaScale
import org.jetbrains.icons.rendering.ImageResource
import org.jetbrains.icons.rendering.PaintingApi
import org.jetbrains.icons.rendering.RescalableImageResource
import org.jetbrains.icons.rendering.ScalingContext
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D

class SwingPaintingApi(
  val c: Component?,
  val g: Graphics,
  val x: Int,
  val y: Int,
  private val customWidth: Int? = null,
  private val customHeight: Int? = null,
  private val overrideColorFilter: ColorFilter? = null,
  override val scaling: ScalingContext = SwingScalingContext(1f)
) : PaintingApi {
  override val bounds: Bounds get() {
    if (c == null) return Bounds(0, 0, customWidth ?: 0, customHeight ?: 0)
    return Bounds(
      (x * scaling.display).toInt(),
      (y * scaling.display).toInt(),
      customWidth ?: (c.width * scaling.display).toInt(),
      customHeight ?: (c.height * scaling.display).toInt()
    )
  }

  override fun getUsedBounds(): Bounds = bounds

  override fun withCustomContext(bounds: Bounds, overrideColorFilter: ColorFilter?): PaintingApi {
    return SwingPaintingApi(c, g, bounds.x, bounds.y, bounds.width, bounds.height, overrideColorFilter ?: this.overrideColorFilter)
  }

  override fun drawCircle(color: Color, x: Int, y: Int, radius: Float, alpha: Float, mode: DrawMode) {
    val r = radius.toDouble()
    drawShape(color, Ellipse2D.Double(x - r, y - r, r + r, r + r), alpha, mode)
  }

  override fun drawRect(color: Color, x: Int, y: Int, width: Int, height: Int, alpha: Float, mode: DrawMode) {
    drawShape(color, Rectangle2D.Double(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble()), alpha, mode)
  }

  private fun drawShape(color: Color, shape: java.awt.Shape, alpha: Float, mode: DrawMode) {
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
        } else {
          g.fill(shape)
        }
      } finally {
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
    colorFilter: ColorFilter?
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
    colorFilter: ColorFilter?
  ) {
    val swingImage = image.scale(FitAreaScale(width ?: bounds.width, height ?: bounds.height))
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
    colorFilter: ColorFilter?
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
    colorFilter: ColorFilter?
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

fun PaintingApi.swing(): SwingPaintingApi? = this as? SwingPaintingApi

internal class SwingScalingContext(
  override val display: Float
): ScalingContext {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SwingScalingContext

    return display == other.display
  }

  override fun hashCode(): Int {
    return display.hashCode()
  }

  override fun toString(): String {
    return "SwingScalingContext(display=$display)"
  }
}