// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.animation.components

import com.intellij.ui.JBColor
import com.intellij.util.animation.Easing
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.CubicCurve2D
import java.awt.geom.Point2D
import javax.swing.JComponent
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

open class BezierPainter(x1: Double, y1: Double, x2: Double, y2: Double) : JComponent() {

  private val controlSize = 10
  private val gridColor = JBColor(Color(0xF0F0F0), Color(0x313335))
  var firstControlPoint: Point2D by Delegates.observable(Point2D.Double(x1, y1), this::fireEvents)
  var secondControlPoint: Point2D by Delegates.observable(Point2D.Double(x2, y2), this::fireEvents)

  private fun fireEvents(prop: KProperty<*>, oldValue: Any?, newValue: Any?) {
    if (oldValue != newValue) {
      firePropertyChange(prop.name, oldValue, newValue)
      repaint()
    }
  }

  init {
    val value = object : MouseAdapter() {
      var i = 0

      override fun mouseDragged(e: MouseEvent) {
        val point = e.point
        when (i) {
          1 -> firstControlPoint = fromScreenXY(point)
          2 -> secondControlPoint = fromScreenXY(point)
        }
        repaint()
      }

      override fun mouseMoved(e: MouseEvent) = with (e.point ) {
        i = when {
          this intersects firstControlPoint -> 1
          this intersects secondControlPoint -> 2
          else -> 0
        }
        if (i != 0) {
          cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        } else {
          cursor = Cursor.getDefaultCursor()
        }
      }

      private infix fun Point.intersects(point: Point2D) : Boolean {
        val xy = toScreenXY(point)
        return xy.x - controlSize / 2 <= x && x <= xy.x + controlSize / 2 &&
               xy.y - controlSize / 2 <= y && y <= xy.y + controlSize / 2
      }
    }
    addMouseMotionListener(value)
    addMouseListener(value)
    minimumSize = Dimension(300, 300)
  }

  fun getEasing() : Easing = Easing.bezier(firstControlPoint.x, firstControlPoint.y, secondControlPoint.x, secondControlPoint.y)

  private fun toScreenXY(point: Point2D) : Point = visibleRect.let { b ->
    val insets = insets
    val width = b.width - (insets.left + insets.right)
    val height = b.height - (insets.top + insets.bottom)
    return Point((point.x * width + insets.left + b.x).toInt(), height - (point.y * height).toInt() + insets.top + b.y)
  }

  private fun fromScreenXY(point: Point) : Point2D.Double = visibleRect.let { b ->
    val insets = insets
    val left = insets.left + b.x
    val top = insets.top + b.y
    val width = b.width - (insets.left + insets.right)
    val height = b.height - (insets.top + insets.bottom)
    val x = (minOf(maxOf(-left, point.x - left), b.width - left).toDouble()) / width
    val y = (minOf(maxOf(-top, point.y - top), b.height - top).toDouble()) / height
    return Point2D.Double(x, 1.0 - y)
  }

  override fun paintComponent(g: Graphics) {
    val bounds = visibleRect
    val insets = insets
    val x = bounds.x + insets.left
    val y = bounds.y + insets.top
    val width = bounds.width - (insets.left + insets.right)
    val height = bounds.height - (insets.top + insets.bottom)
    val g2d = g.create() as Graphics2D

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    g2d.color = JBColor.background()
    g2d.fillRect(0, 0, bounds.width, bounds.height)

    g2d.color = gridColor
    for (i in 0..4) {
      g2d.drawLine(x + width * i / 4, y, x + width * i / 4, y + height)
      g2d.drawLine(x,y + height * i / 4, x + width, y + height * i / 4)
    }

    val (x0, y0) = toScreenXY(Point2D.Double(0.0, 0.0))
    val (x1, y1) = toScreenXY(firstControlPoint)
    val (x2, y2) = toScreenXY(secondControlPoint)
    val (x3, y3) = toScreenXY(Point2D.Double(1.0, 1.0))

    val bez = CubicCurve2D.Double(x0, y0, x1, y1, x2, y2, x3, y3)

    g2d.color = JBColor.foreground()
    g2d.stroke = BasicStroke(2f)
    g2d.draw(bez)
    g2d.stroke = BasicStroke(1f)
    g2d.color = when {
      isEnabled -> Color(151, 118, 169)
      JBColor.isBright() -> JBColor.LIGHT_GRAY
      else -> JBColor.GRAY
    }
    g2d.fillOval(x1.toInt() - controlSize / 2, y1.toInt() - controlSize / 2, controlSize, controlSize)
    g2d.drawLine(x0.toInt(), y0.toInt(), x1.toInt(), y1.toInt())
    g2d.color = when {
      isEnabled -> Color(208, 167, 8)
      JBColor.isBright() -> JBColor.LIGHT_GRAY
      else -> JBColor.GRAY
    }
    g2d.fillOval(x2.toInt() - controlSize / 2, y2.toInt() - controlSize / 2, controlSize, controlSize)
    g2d.drawLine(x2.toInt(), y2.toInt(), x3.toInt(), y3.toInt())
    g2d.dispose()
  }

  private operator fun Point2D.component1() = x
  private operator fun Point2D.component2() = y
}