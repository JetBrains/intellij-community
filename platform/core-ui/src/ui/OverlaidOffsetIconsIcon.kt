// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.swing.Icon
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Paints [icons] in a stack - one over the other with a slight offset
 * Assumes that icons all have the same size
 */
@ApiStatus.NonExtendable
open class OverlaidOffsetIconsIcon(
  private val icons: List<Icon>,
  private val offsetRate: Float = 0.4f
) : Icon {

  override fun getIconHeight(): Int = icons.maxOfOrNull { it.iconHeight } ?: 0

  override fun getIconWidth(): Int {
    if (icons.isEmpty()) return 0
    val iconWidth = icons.first().iconWidth
    val width = iconWidth + (icons.size - 1) * iconWidth * offsetRate
    return max(width.roundToInt(), 0)
  }

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val bufferImage = ImageUtil.createImage(g, iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB)
    val bufferGraphics = bufferImage.createGraphics()
    try {
      paintToBuffer(c, bufferGraphics)
    }
    finally {
      bufferGraphics.dispose()
    }
    StartupUiUtil.drawImage(g, bufferImage, x, y, null)
  }

  private fun paintToBuffer(c: Component?, g: Graphics2D) {
    var rightEdge = iconWidth
    icons.reversed().forEachIndexed { index, icon ->
      val currentX = rightEdge - icon.iconWidth

      // cut out the part of the painted icon slightly bigger then the next one to create a visual gap
      if (index > 0) {
        val g2 = g.create() as Graphics2D
        try {
          val scaleX = 1.1
          val scaleY = 1.1
          // paint a bit higher, so that the cutout is centered
          g2.translate(0, -((iconHeight * scaleY - iconHeight) / 2).roundToInt())
          g2.scale(scaleX, scaleY)
          g2.composite = AlphaComposite.DstOut
          icon.paintIcon(c, g2, currentX, 0)
        }
        finally {
          g2.dispose()
        }
      }

      icon.paintIcon(c, g, currentX, 0)
      rightEdge -= (icon.iconWidth * offsetRate).roundToInt()
    }
  }
}