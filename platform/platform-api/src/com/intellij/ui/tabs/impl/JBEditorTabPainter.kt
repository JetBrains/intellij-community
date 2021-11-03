// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.impl.themes.EditorTabTheme
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle

class JBEditorTabPainter : JBDefaultTabPainter(EditorTabTheme()) {
  override fun paintTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, active: Boolean, hovered: Boolean) {
    updatedRect(position, rect, borderThickness)
    super.paintTab(position, g, rect, borderThickness, tabColor, active, hovered)
  }

  private fun updatedRect(position: JBTabsPosition, rect: Rectangle, borderThickness: Int): Rectangle {
    when (position) {
      JBTabsPosition.bottom -> rect.y += borderThickness
      JBTabsPosition.right -> rect.x += borderThickness
      else -> { }
    }
    return rect
  }

  override fun paintSelectedTab(position: JBTabsPosition,
                                g: Graphics2D,
                                rect: Rectangle,
                                borderThickness: Int,
                                tabColor: Color?,
                                active: Boolean,
                                hovered: Boolean) {

    updatedRect(position, rect, borderThickness)
    super.paintSelectedTab(position, g, rect, borderThickness, tabColor, active, hovered)
  }


  fun paintLeftGap(position: JBTabsPosition, g: Graphics2D, baseRect: Rectangle, borderThickness: Int) {
    val rect = updatedRect(position, baseRect.clone() as Rectangle, borderThickness)

    val maxY = rect.y + rect.height - borderThickness

    paintBorderLine(g, borderThickness, Point(rect.x, rect.y), Point(rect.x, maxY))
  }

  fun paintRightGap(position: JBTabsPosition, g: Graphics2D, baseRect: Rectangle, borderThickness: Int) {
    val rect = updatedRect(position, baseRect.clone() as Rectangle, borderThickness)

    val maxX = rect.x + rect.width - borderThickness
    val maxY = rect.y + rect.height - borderThickness

    paintBorderLine(g, borderThickness, Point(maxX, rect.y), Point(maxX, maxY))
  }

  fun paintTopGap(position: JBTabsPosition, g: Graphics2D, baseRect: Rectangle, borderThickness: Int) {
    val rect = updatedRect(position, baseRect.clone() as Rectangle, borderThickness)

    val maxX = rect.x + rect.width

    paintBorderLine(g, borderThickness, Point(rect.x, rect.y), Point(maxX, rect.y))
  }

  fun paintBottomGap(position: JBTabsPosition, g: Graphics2D, baseRect: Rectangle, borderThickness: Int) {
    val rect = updatedRect(position, baseRect.clone() as Rectangle, borderThickness)

    val maxX = rect.x + rect.width - borderThickness
    val maxY = rect.y + rect.height - borderThickness

    paintBorderLine(g, borderThickness, Point(rect.x, maxY), Point(maxX, maxY))
  }

  override fun underlineRectangle(position: JBTabsPosition, rect: Rectangle, thickness: Int): Rectangle {
    return when (position) {
      JBTabsPosition.bottom -> Rectangle(rect.x, rect.y, rect.width, thickness)
      JBTabsPosition.left -> Rectangle(rect.x + rect.width - thickness, rect.y, thickness, rect.height)
      JBTabsPosition.right -> Rectangle(rect.x, rect.y, thickness, rect.height)
      else -> super.underlineRectangle(position, rect, thickness)
    }
  }
}