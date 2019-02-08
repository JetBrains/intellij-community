// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.ui.tabs.JBTabsBorder
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.util.ui.JBUI
import java.awt.*

class EditorJBTabsBorder(val tabs: JBTabsImpl): JBTabsBorder {
  private var thickness: Int = 1

  override fun setThickness(value: Int) {
    thickness = value
  }

  override fun getThickness(): Int = thickness

  override fun getBorderInsets(c: Component?): Insets = JBUI.emptyInsets()

  override fun getEffectiveBorder(): Insets = Insets(thickness, if(tabs.position == JBTabsPosition.right) thickness else 0, 0, 0)

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val headerRectangle = tabs.getLastLayoutPass().headerRectangle;
    val th = thickness.toDouble()

    val startY = headerRectangle.y - if(tabs.position == JBTabsPosition.bottom) 0 else thickness
    tabs.getTabPainter().paintBorderLine(g as Graphics2D, th, Point(x, startY), Point(x + width, startY))

    if(tabs.position == JBTabsPosition.top){
      for (eachRow in 1..tabs.lastLayoutPass.rowCount) {
        val yl = (eachRow * tabs.myHeaderFitSize.height) + startY
        tabs.getTabPainter().paintBorderLine(g, th, Point(x, yl), Point(x + width, yl))
      }
    } else if(tabs.position == JBTabsPosition.bottom) {
      tabs.getTabPainter().paintBorderLine(g, th, Point(x, y), Point(x + width, y))
    }
  }
}