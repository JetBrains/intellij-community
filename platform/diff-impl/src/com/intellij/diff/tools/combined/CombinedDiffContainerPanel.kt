// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBInsets
import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import kotlin.properties.Delegates

/**
 * A very special panel that does optimized painting for rounded combined diff panels
 */
internal open class CombinedDiffContainerPanel(
  layout: LayoutManager?, var roundedBottom: Boolean
) : JPanel(layout) {
  private val arcRadius: Int = CombinedDiffUI.BLOCK_ARC

  private val borderThickness: Int = 1
  var borderColor: Color by Delegates.observable(CombinedDiffUI.EDITOR_BORDER_COLOR) { _, oldValue, newValue ->
    if (oldValue != newValue) repaint()
  }

  var bottomBorderColor: Color by Delegates.observable(CombinedDiffUI.EDITOR_BORDER_COLOR) { _, oldValue, newValue ->
    if (oldValue != newValue) repaint()
  }

  init {
    // unscaled border for correct insets
    @Suppress("UseDPIAwareBorders")
    border = EmptyBorder(borderThickness, borderThickness, borderThickness, borderThickness)
  }

  override fun paint(g: Graphics) {
    super.paint(g)

    val outerBounds = Rectangle(Point(), size)
    val innerBounds = Rectangle(Point(), size).apply { JBInsets.removeFrom(this, insets) }

    val clipBounds = g.clipBounds
    val paintTopCap = intersectsTopCap(clipBounds, outerBounds)
    val paintBottomCap = intersectsBottomCap(clipBounds, outerBounds)

    val g2 = g.create() as Graphics2D
    try {
      if (paintTopCap) {
        paintCap(g2, innerBounds, outerBounds, true)
      }

      paintBody(g2, clipBounds, outerBounds)

      if (paintBottomCap) {
        if (roundedBottom) {
          paintCap(g2, innerBounds, outerBounds, false)
        }
        else {
          paintBottomBoxOutline(g2, outerBounds)
        }
      }
    }
    finally {
      g2.dispose()
    }
  }

  private fun paintCap(g: Graphics2D, innerBounds: Rectangle, outerBounds: Rectangle, top: Boolean) {
    GraphicsUtil.setupAAPainting(g)
    val arcRadius2D = arcRadius.toDouble()
    val fillColor = background
    if (fillColor != null) smoothCorners(g, innerBounds, arcRadius2D, fillColor, top)
    paintRoundedOutline(g, innerBounds, outerBounds, arcRadius2D, top)
  }

  private fun paintRoundedOutline(g: Graphics2D, innerBounds: Rectangle, outerBounds: Rectangle, arcRadius2D: Double, top: Boolean) {
    // we do fill here, because draw with AA screws us over and puts the lines outside the paint box
    val border = Path2D.Double(Path2D.WIND_EVEN_ODD).apply {
      appendRoundedBoxOutline(innerBounds, arcRadius2D, top)
      appendRoundedBoxOutline(outerBounds, arcRadius2D + borderThickness, top)
    }
    g.color = borderColor
    g.fill(border)
  }

  private fun smoothCorners(g: Graphics2D, bounds: Rectangle, arcRadius2D: Double, fillColor: Color, top: Boolean) {
    val cap = Path2D.Double(Path2D.WIND_EVEN_ODD).apply {
      appendRoundedBoxOutline(bounds, arcRadius2D, top)
      appendBoxOutline(bounds, arcRadius2D, top)
    }
    g.color = fillColor
    g.fill(cap)
  }

  private fun paintBody(g: Graphics, clipBounds: Rectangle, outerBounds: Rectangle) {
    val bodyBounds = Rectangle(outerBounds.x, outerBounds.y + arcRadius, outerBounds.width, outerBounds.height - arcRadius * 2)
    val toPaint = bodyBounds.intersection(Rectangle(outerBounds.x, clipBounds.y, outerBounds.width, clipBounds.height))
    if (toPaint.height <= 0) return

    GraphicsUtil.disableAAPainting(g)
    g.color = borderColor
    // left
    g.fillRect(toPaint.x, toPaint.y, borderThickness, toPaint.height)
    // right
    g.fillRect(toPaint.x + toPaint.width - borderThickness, toPaint.y, borderThickness, toPaint.height)
  }

  private fun paintBottomBoxOutline(g: Graphics2D, outerBounds: Rectangle) {
    GraphicsUtil.disableAAPainting(g)

    g.color = bottomBorderColor //sticky header bottom border color

    // bottom
    g.fillRect(outerBounds.x, outerBounds.y + height - borderThickness, outerBounds.width, borderThickness)

    g.color = borderColor

    val sideY = outerBounds.y + height - arcRadius
    // left
    g.fillRect(outerBounds.x, sideY, borderThickness, arcRadius)
    // right
    g.fillRect(outerBounds.x + outerBounds.width - borderThickness, sideY, borderThickness, arcRadius)
  }

  private fun intersectsTopCap(clipBounds: Rectangle, outerBounds: Rectangle) =
    clipBounds.y in outerBounds.y..arcRadius

  private fun intersectsBottomCap(clipBounds: Rectangle, outerBounds: Rectangle): Boolean {
    return clipBounds.y + clipBounds.height in outerBounds.height - arcRadius..outerBounds.height
  }

  private fun Path2D.appendBoxOutline(rect2D: Rectangle2D, outlineHeight: Double, top: Boolean) {
    val x = rect2D.x
    val y = rect2D.y
    val width = rect2D.width
    val height = rect2D.height

    val yShift = if (top) 0.0 else height
    val yArcShift = if (top) outlineHeight else height - outlineHeight

    moveTo(x, y + yArcShift)
    lineTo(x, y + yShift)
    lineTo(x + width, y + yShift)
    lineTo(x + width, y + yArcShift)
  }

  private fun Path2D.appendRoundedBoxOutline(rect2D: Rectangle2D, arcRadius2D: Double, top: Boolean) {
    val x = rect2D.x
    val y = rect2D.y
    val width = rect2D.width
    val height = rect2D.height

    val yShift = if (top) 0.0 else height
    val yArcShift = if (top) arcRadius2D else height - arcRadius2D

    moveTo(x, y + yArcShift)
    quadTo(x, y + yShift, x + arcRadius2D, y + yShift)
    lineTo(x + width - arcRadius2D, y + yShift)
    quadTo(x + width, y + yShift, x + width, y + yArcShift)
  }

  // From Swing's point of view children are painted in a rectangular box,
  // so when a repaint happens on a child, this panel will not clip the corners.
  // This property causes repaint of a child to trigger repaint of this panel.
  override fun isPaintingOrigin(): Boolean = true
}
