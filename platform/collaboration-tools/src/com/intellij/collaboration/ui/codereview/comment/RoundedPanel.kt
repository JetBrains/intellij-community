// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Obsolete
import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.VolatileImage
import javax.swing.JPanel

/**
 * Do not create directly - use [com.intellij.collaboration.ui.ClippingRoundedPanel]
 * This panel clips the rounded corners of its children and background and allows the underlying background to show through.
 * This is achieved by painting to off-screen buffer first, clearing the corners and then painting said buffer to the graphics, which
 * is very ineffective.
 */
@ApiStatus.Internal
class RoundedPanel @Obsolete constructor(layout: LayoutManager?, private val arcRadius: Int = 8) : JPanel(layout) {
  private var buffer: VolatileImage? = null
  private var fillBackground: Boolean = false

  init {
    isOpaque = false
    cursor = Cursor.getDefaultCursor()
  }

  override fun setOpaque(isOpaque: Boolean) {} // Disable opaque

  override fun paint(g: Graphics) {
    fillBackground = false
    super.paint(g)
  }

  override fun paintComponent(g: Graphics) {
    // will be called inside paint if the panel is not fully obscured by children
    fillBackground = true
  }

  //TODO: optimize with clip
  override fun paintChildren(g: Graphics) {
    val g2 = g.create() as? Graphics2D ?: return
    try {
      val componentBounds = Rectangle(size).also {
        JBInsets.removeFrom(it, insets)
      }
      val outsideShape = createOutsideShape(size, componentBounds, arcRadius - 1)

      val buffer = validateAndRecreateBuffer(g2) ?: return
      val painted = paintToVolatileImage(buffer) { bufferG2 ->
        paintAndSmooth(bufferG2, componentBounds, outsideShape)
        super.paintBorder(bufferG2)
      }

      if (!painted) return
      GraphicsUtil.disableAAPainting(g2)
      PaintUtil.alignTxToInt(g2, null, true, true, PaintUtil.RoundingMode.ROUND_FLOOR_BIAS)
      g2.drawImage(buffer, 0, 0, null)
    }
    finally {
      g2.dispose()
    }
  }

  // paint background and children first and then clear the area outside the desired shape
  private fun paintAndSmooth(g2: Graphics2D, componentBounds: Rectangle, outsideShape: Shape) {
    if (fillBackground && isBackgroundSet) {
      g2.color = background
      g2.fill(componentBounds)
    }

    super.paintChildren(g2)

    GraphicsUtil.setupAAPainting(g2)
    val composite = g2.composite
    g2.composite = AlphaComposite.Clear
    g2.fill(outsideShape)
    g2.composite = composite
  }

  // border will be painter after children to avoid clipping
  override fun paintBorder(g: Graphics?) = Unit

  // From Swing's point of view children are painted in a rectangular box,
  // so when a repaint happens on a child, this panel will not clip the corners.
  // This property causes repaint of a child to trigger repaint of this panel.
  override fun isPaintingOrigin(): Boolean = true

  private fun validateAndRecreateBuffer(g2: Graphics2D): VolatileImage? {
    /*
    Where do I begin?
    A VolatileImage (VI) is created with the provided user size and the same scale as in g2.transform (system scale).
    When painting to this image, we effectively paint the same way we would paint to the g2 (this is good).
    BUT when it comes to transferring the image to the "screen" we have to use g2.drawImage,
     which delegates to SunGraphics2D.drawHiDPIImage.
    For some unknown-to-me reason, there's no way to just do "transfer the data from one surface to another with the same scaling".
    To decide if the image actualy has to be scale-printed, SunGraphics2D does the following:
    image size is scaled by VI scale, rounded UP and THEN compared to the size scaled by g2 scale.
    A lot of times this leads to off-by-one errors.

    So what we do here is we intentionally make image size such that scaling and rounding up
     will always result in a value equal to just scaling.
    */
    val ctx = ScaleContext.create(g2)
    val width = PaintUtil.alignIntToInt(width, ctx, PaintUtil.RoundingMode.CEIL, null)
    val height = PaintUtil.alignIntToInt(height, ctx, PaintUtil.RoundingMode.CEIL, null)
    if (width <= 0 || height <= 0) return null

    val dc = g2.deviceConfiguration
    return buffer?.takeIf {
      it.width == width && it.height == height && it.validate(dc) != VolatileImage.IMAGE_INCOMPATIBLE
    } ?: dc.createCompatibleVolatileImage(width, height, Transparency.BITMASK)?.takeIf {
      it.validate(dc) != VolatileImage.IMAGE_INCOMPATIBLE
    }.also {
      buffer = it
    }
  }
}

private fun createOutsideShape(overallSize: Dimension, componentBounds: Rectangle, arcRadius: Int): Shape =
  Path2D.Double(Path2D.WIND_EVEN_ODD).apply {
    // inner
    val innerRect = componentBounds.bounds2D
    val arc = arcRadius * 2.0
    append(RoundRectangle2D.Double(innerRect.x, innerRect.y,
                                   innerRect.width, innerRect.height,
                                   arc, arc), false)
    // outer
    val outerRect = Rectangle(overallSize).also {
      @Suppress("UseDPIAwareInsets")
      JBInsets.addTo(it, Insets(1, 1, 1, 1))
    }
    append(outerRect, false)
  }

private fun paintToVolatileImage(image: VolatileImage, painter: (g2: Graphics2D) -> Unit): Boolean {
  var iteration = 0
  do {
    iteration++
    val bufferG = image.createGraphics()
    try {
      painter(bufferG)
    }
    finally {
      bufferG.dispose()
    }
  }
  while (image.contentsLost() && iteration <= 3)
  return !image.contentsLost()
}
