// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.GraphicsUtil
import org.intellij.lang.annotations.MagicConstant
import java.awt.*
import java.awt.image.VolatileImage

/**
 * Allows painting with the help of an intermediate buffer
 * This in turn allows post-processing of the painted data without affecting the actual target surface
 *
 * This implementation uses [VolatileImage] as a buffer, which can sometimes be hardware accelerated
 */
internal class VolatileImageBufferingPainter(@MagicConstant(valuesFromClass = Transparency::class) private val bufferTransparency: Int) {
  private var buffer: VolatileImage? = null

  fun paintBuffered(targetG: Graphics, bufferSize: Dimension, painter: (bufferG2: Graphics2D) -> Unit) {
    val g2 = targetG.create() as? Graphics2D ?: return
    try {
      val buffer = validateAndRecreateBuffer(g2, bufferSize) ?: return
      val painted = paintToVolatileImage(buffer, painter)
      if (!painted) return
      GraphicsUtil.disableAAPainting(g2)
      PaintUtil.alignTxToInt(g2, null, true, true, PaintUtil.RoundingMode.ROUND_FLOOR_BIAS)
      g2.drawImage(buffer, 0, 0, null)
    }
    finally {
      g2.dispose()
    }
  }

  //TODO: recreate buffer on system scale change
  private fun validateAndRecreateBuffer(g2: Graphics2D, bufferSize: Dimension): VolatileImage? {
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
    val widthAligned = PaintUtil.alignIntToInt(bufferSize.width, ctx, PaintUtil.RoundingMode.CEIL, null)
    val heightAligned = PaintUtil.alignIntToInt(bufferSize.height, ctx, PaintUtil.RoundingMode.CEIL, null)
    if (widthAligned <= 0 || heightAligned <= 0) return null

    val dc = g2.deviceConfiguration
    return buffer?.takeIf {
      it.width == widthAligned && it.height == heightAligned && it.validate(dc) != VolatileImage.IMAGE_INCOMPATIBLE
    } ?: createVolatileImage(dc, widthAligned, heightAligned).also {
      buffer = it
    }
  }

  private fun createVolatileImage(dc: GraphicsConfiguration, width: Int, height: Int): VolatileImage? =
    try {
      // acceleration does not work for BITMASK so we defer to full transparency
      val transparency = if (bufferTransparency != Transparency.OPAQUE) Transparency.TRANSLUCENT else bufferTransparency
      dc.createCompatibleVolatileImage(width, height, ImageCapabilities(true), transparency)
    }
    catch (_: AWTException) {
      dc.createCompatibleVolatileImage(width, height, bufferTransparency)
    }?.takeIf {
      it.validate(dc) != VolatileImage.IMAGE_INCOMPATIBLE
    }
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