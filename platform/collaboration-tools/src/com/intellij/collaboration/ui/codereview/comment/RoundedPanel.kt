// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.collaboration.ui.util.VolatileImageBufferingPainter
import com.intellij.ui.paint.RectanglePainter
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Obsolete
import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel

/**
 * Do not create directly - use [com.intellij.collaboration.ui.ClippingRoundedPanel]
 * This panel clips the rounded corners of its children and background and allows the underlying background to show through.
 * This is achieved by painting to off-screen buffer first, clearing the corners and then painting said buffer to the graphics, which
 * is very ineffective.
 */
@ApiStatus.Internal
class RoundedPanel @Obsolete constructor(layout: LayoutManager?, private val arcRadius: Int = 8) : JPanel(layout) {
  private var bufferingPainter = VolatileImageBufferingPainter(Transparency.TRANSLUCENT)
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
    val componentBounds = Rectangle(size).also {
      JBInsets.removeFrom(it, insets)
    }
    val outsideShape = createOutsideShape(size, componentBounds, arcRadius - 1)
    bufferingPainter.paintBuffered(g, size) {
      paintAndSmooth(it, componentBounds, outsideShape)
    }
  }

  private fun paintAndSmooth(g2: Graphics2D, componentBounds: Rectangle, outsideShape: Shape) {
    paintBackground(g2, componentBounds)
    super.paintChildren(g2)
    clearArea(g2, outsideShape)
    super.paintBorder(g2)
  }

  private fun paintBackground(g2: Graphics2D, area: Shape) {
    if (fillBackground && isBackgroundSet) {
      g2.color = background
      val rect = area.bounds
      val arc = arcRadius * 2
      RectanglePainter.FILL.paint(g2, rect.x, rect.y, rect.width, rect.height, arc)
    }
  }

  private fun clearArea(g2: Graphics2D, area: Shape) {
    // AA disabled for now because it tremendously slows down the painting
    //GraphicsUtil.setupAAPainting(g2)
    val composite = g2.composite
    g2.composite = AlphaComposite.Clear
    g2.fill(area)
    g2.composite = composite
  }

  // border will be painter after children to avoid clipping
  override fun paintBorder(g: Graphics?) = Unit

  // From Swing's point of view children are painted in a rectangular box,
  // so when a repaint happens on a child, this panel will not clip the corners.
  // This property causes repaint of a child to trigger repaint of this panel.
  override fun isPaintingOrigin(): Boolean = true
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