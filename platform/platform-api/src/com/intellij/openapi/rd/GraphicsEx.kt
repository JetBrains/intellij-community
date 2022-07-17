// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd

import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.paint.RectanglePainter2D
import java.awt.*

fun Graphics2D.fill2DRect(rect: Rectangle, color: Color) {
    this.color = color
    RectanglePainter2D.FILL.paint(this, rect.x.toDouble(), rect.y.toDouble(), rect.width.toDouble(), rect.height.toDouble())
}

fun Graphics2D.fill2DRoundRect(rect: Rectangle, arcRadius: Double, color: Color) {
    this.color = color
    RectanglePainter2D.FILL.paint(this, rect.x.toDouble(), rect.y.toDouble(), rect.width.toDouble(), rect.height.toDouble(), arcRadius, LinePainter2D.StrokeType.INSIDE, 1.0, RenderingHints.VALUE_ANTIALIAS_ON)
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

