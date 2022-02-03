// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DuplicatedCode") // extracted from org.jetbrains.r.rendering.toolwindow.RDocumentationComponent

package com.intellij.lang.documentation.ide.ui

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.ColorUtil
import java.awt.*
import javax.swing.text.Highlighter
import javax.swing.text.JTextComponent

internal class SearchHighlighterPainter(private val current: Boolean) : Highlighter.HighlightPainter {

  override fun paint(g: Graphics, p0: Int, p1: Int, bounds: Shape, c: JTextComponent) {
    val mapper = c.ui

    val rec0: Rectangle = mapper.modelToView(c, p0)
    val rec1: Rectangle = mapper.modelToView(c, p1)

    val borderColor = getBorderColor(current)
    val backgroundColor = getBackgroundColor(current)
    val g2d = g.create() as Graphics2D
    try {
      // one line selection
      if (rec0.y == rec1.y) {
        val target = rec0.union(rec1)
        drawRectangle(g2d, target, backgroundColor, borderColor)
      }
      else {
        drawMultiLineSelection(bounds, rec0, rec1, g2d, backgroundColor, borderColor)
      }
    }
    finally {
      g2d.dispose()
    }
  }
}

private fun drawRectangle(
  g2d: Graphics2D,
  target: Rectangle,
  backgroundColor: Color?,
  borderColor: Color?,
) {
  if (backgroundColor != null) {
    g2d.color = backgroundColor
    g2d.fillRect(target.x, target.y, target.width, target.height - 1)
  }
  if (borderColor != null) {
    g2d.color = borderColor
    g2d.drawRect(target.x, target.y, target.width, target.height - 1)
  }
}

private fun drawMultiLineSelection(
  bounds: Shape,
  rec0: Rectangle,
  rec1: Rectangle,
  g2d: Graphics2D,
  backgroundColor: Color?,
  borderColor: Color?,
) {
  val area = bounds.bounds
  val rec0ToMarginWidth = area.x + area.width - rec0.x
  val secondLineY = rec0.y + rec0.height
  val firstLineRectangle = Rectangle(rec0.x, rec0.y, rec0ToMarginWidth, rec0.height)
  val lastLineRectangle = Rectangle(area.x, rec1.y, (rec1.x - area.x), rec1.height)
  // draw first line
  drawRectangle(g2d, firstLineRectangle, backgroundColor, borderColor)
  // draw middle lines
  val multiline = secondLineY != rec1.y
  if (multiline) {
    drawRectangle(g2d, Rectangle(area.x, secondLineY, area.width, rec1.y - secondLineY), backgroundColor, borderColor)
    // clear border between the first and the middle
    g2d.color = backgroundColor
    g2d.fillRect(firstLineRectangle.x, firstLineRectangle.y + firstLineRectangle.height - 2, firstLineRectangle.width - 1, 5)
  }
  // draw last line
  drawRectangle(g2d, lastLineRectangle, backgroundColor, borderColor)
  g2d.color = backgroundColor
  if (multiline) {
    // clear border between the middle and the last
    g2d.fillRect(lastLineRectangle.x + 1, lastLineRectangle.y - 2, lastLineRectangle.width - 1, 5)
  }
  else {
    val lastMaxX = lastLineRectangle.x + lastLineRectangle.width
    // clear border between the first and the last
    if (firstLineRectangle.x + 1 <= lastMaxX - 1) {
      g2d.fillRect(firstLineRectangle.x + 1, lastLineRectangle.y - 2, lastMaxX - firstLineRectangle.x - 1, 5)
    }
  }
}

private fun getBackgroundColor(current: Boolean): Color? {
  val globalScheme = EditorColorsManager.getInstance().globalScheme
  return if (current) {
    if (ColorUtil.isDark(globalScheme.defaultBackground)) {
      EditorColorsManager.getInstance().globalScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR)
    }
    else {
      globalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES).backgroundColor
    }
  }
  else {
    globalScheme.getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES).backgroundColor
  }
}

private fun getBorderColor(current: Boolean): Color? {
  if (current) {
    return EditorColorsManager.getInstance().globalScheme.defaultForeground
  }
  else {
    return null
  }
}
