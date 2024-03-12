// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.ui.RoundedLineBorder
import com.intellij.util.ui.GraphicsUtil
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LayoutManager
import java.awt.geom.Path2D
import javax.swing.JPanel

internal class FillingRoundedRectanglePanel(layout: LayoutManager?, arc: Int = 8) : JPanel(layout) {
  private val borderArc = arc + 2
  private val fillRadius = arc.toDouble() - 1
  var fillColor: Color? = null

  init {
    isOpaque = true
    background = null
    border = RoundedLineBorder(CombinedDiffUI.EDITOR_BORDER_COLOR, borderArc)
  }

  override fun paint(g: Graphics) {
    super.paint(g)
    val fillColor = fillColor
    if (fillColor != null) {
      val corners = createTransparentCorners()
      val g2 = g.create() as Graphics2D
      try {
        g2.color = fillColor
        GraphicsUtil.setupAAPainting(g2)
        val clipBounds = g2.clipBounds
        for (corner in corners) {
          if (clipBounds.intersects(corner.bounds)) {
            g2.fill(corner)
          }
        }
      }
      finally {
        g2.dispose()
      }
    }

    // we have to paint the border AFTER painting children because rounded border corners will be painted over otherwise
    border?.run { paintBorder(this@FillingRoundedRectanglePanel, g, 0, 0, width, height) }
  }

  // From Swing's point of view children are painted in a rectangular box,
  // so when a repaint happens on a child, this panel will not clip the corners.
  // This property causes repaint of a child to trigger repaint of this panel.
  override fun isPaintingOrigin(): Boolean = true

  private fun createTransparentCorners(): List<Path2D> {
    val width = width.toDouble()
    val height = height.toDouble()
    return listOf(
      Path2D.Double().apply { // top left
        moveTo(0.0, 0.0)
        lineTo(0.0, fillRadius)
        quadTo(0.0, 0.0, fillRadius, 0.0)
        closePath()
      },
      Path2D.Double().apply { // top right
        moveTo(width - fillRadius, 0.0)
        quadTo(width, 0.0, width, fillRadius)
        lineTo(width, 0.0)
        closePath()
      },
      Path2D.Double().apply { // bottom left
        moveTo(0.0, height - fillRadius)
        lineTo(0.0, height)
        lineTo(fillRadius, height)
        quadTo(0.0, height, 0.0, height - fillRadius)
      },
      Path2D.Double().apply { // bottom right
        moveTo(width, height - fillRadius)
        lineTo(width, height)
        lineTo(width - fillRadius, height)
        quadTo(width, height, width, height - fillRadius)
      }
    )
  }

  override fun paintBorder(g: Graphics) {
    // we will paint border manually
  }
}