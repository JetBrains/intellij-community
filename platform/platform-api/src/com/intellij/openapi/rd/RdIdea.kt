// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd

import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.paint.RectanglePainter2D
import com.jetbrains.rd.swing.awtMousePoint
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.ISource
import com.jetbrains.rd.util.reactive.map
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JComponent
import javax.swing.SwingUtilities

fun IdeGlassPane.mouseMoved(): ISource<MouseEvent> {
  return object : ISource<MouseEvent> {
    override fun advise(lifetime: Lifetime, handler: (MouseEvent) -> Unit) {
      val listener = object : MouseMotionAdapter() {
        override fun mouseMoved(e: MouseEvent?) {
          if (e != null) {
            handler(e)
          }
        }
      }

      this@mouseMoved.addMouseMotionPreprocessor(listener, lifetime.createNestedDisposable())
    }
  }
}

fun IdeGlassPane.childAtMouse(container: Container): ISource<Component?> = this@childAtMouse.mouseMoved()
  .map { SwingUtilities.convertPoint(it.component, it.x, it.y, container) }
  .map { container.getComponentAt(it) }


fun JComponent.childAtMouse(): IPropertyView<Component?> = this@childAtMouse.awtMousePoint()
  .map {
    if (it == null) null
    else {
      this@childAtMouse.getComponentAt(it)
    }
  }

fun Graphics2D.fill2DRect(rect: Rectangle, color: Color) {
  this.color = color
  RectanglePainter2D.FILL.paint(this, rect.x.toDouble(), rect.y.toDouble(), rect.width.toDouble(), rect.height.toDouble())
}

fun Graphics2D.paint2DLine(from: Point, to: Point,
                           strokeType: LinePainter2D.StrokeType,
                           strokeWidth: Double, color: Color) {
  this.paint2DLine(from.getX(), from.getY(), to.getX(), to.getY(), strokeType, strokeWidth, color)
}

fun Graphics2D.paint2DLine(x1: Double, y1: Double, x2: Double, y2: Double,
                           strokeType: LinePainter2D.StrokeType,
                           strokeWidth: Double, color: Color) {
  this.color = color
  LinePainter2D.paint(this, x1, y1, x2, y2, strokeType,
                      strokeWidth)
}

