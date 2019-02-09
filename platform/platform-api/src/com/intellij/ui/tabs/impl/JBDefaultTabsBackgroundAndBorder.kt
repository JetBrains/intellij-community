// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import java.awt.*

open class JBDefaultTabsBackgroundAndBorder(tabs: JBTabsImpl) : JBBaseTabsBackgroundAndBorder(tabs) {
  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    if (tabs.isEmptyVisible) return
    g as Graphics2D

    super.paintBorder(c, g, x, y, width, height);

    val rect = Rectangle(x, y, width, height)
    val headerRectangle = tabs.lastLayoutPass.headerRectangle;
    val maxY = headerRectangle.maxY.toInt() - thickness

    tabs.getTabPainter().paintBorderLine(g, thickness, Point(rect.x, maxY), Point(rect.maxX.toInt(), maxY))
  }
}