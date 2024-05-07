// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.charts.ui

import java.awt.*

fun Graphics2D.withColor(color: Color, block: Graphics2D.() -> Unit): Graphics2D {
  val oldColor = this.color
  this.color = color
  block()
  this.color = oldColor
  return this
}

fun Graphics2D.withFont(font: Font, block: Graphics2D.() -> Unit): Graphics2D {
  val oldFont = this.font
  this.font = font
  block()
  this.font = oldFont
  return this
}

fun Graphics2D.withAntialiasing(block: Graphics2D.() -> Unit): Graphics2D {
  val old = getRenderingHint(RenderingHints.KEY_ANTIALIASING)

  setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
  block()

  setRenderingHint(RenderingHints.KEY_ANTIALIASING, old)
  return this
}

fun Graphics2D.withStroke(stroke: Stroke, block: Graphics2D.() -> Unit): Graphics2D {
  val oldStroke = this.stroke
  this.stroke = stroke
  block()
  this.stroke = oldStroke
  return this
}
