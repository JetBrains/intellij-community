// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.ui.tabs.JBTabsBorder
import com.intellij.util.ui.JBUI
import java.awt.*

class DefaultJBTabsBorder(val tabs: JBTabsImpl): JBTabsBorder {
  private var thickness: Int = 1

  override fun setThickness(value: Int) {
    value ?: return
    thickness = value
  }

  override fun getThickness(): Int = thickness

  override fun getBorderInsets(c: Component?): Insets = JBUI.emptyInsets()

  override fun getEffectiveBorder(): Insets = JBUI.emptyInsets()

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val rect = Rectangle(x, y, width, height)
    val headerRectangle = tabs.getLastLayoutPass().headerRectangle;
    val maxY = (headerRectangle.maxY - thickness).toInt()

    tabs.getTabPainter().paintBorderLine(g as Graphics2D, thickness.toDouble(), Point(rect.x, maxY), Point(rect.maxX.toInt(), maxY))
  }
}