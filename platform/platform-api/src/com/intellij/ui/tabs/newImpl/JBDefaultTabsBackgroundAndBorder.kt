// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl

import com.intellij.ui.tabs.JBTabsBackgroundAndBorder
import java.awt.*

open class JBDefaultTabsBackgroundAndBorder(tabs: JBTabsImpl) : JBTabsBackgroundAndBorder(tabs) {
  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    if (tabs.isEmptyVisible) return
    g as Graphics2D

    val rect = Rectangle(x, y, width, height)

    paintBackground(g, rect)


    val firstLabel = tabs.myInfo2Label.get(tabs.lastLayoutPass.getTabAt(0, 0)) ?: return
    val maxY = firstLabel.bounds.maxY.toInt() - thickness

    tabs.tabPainter.paintBorderLine(g, thickness, Point(rect.x, maxY), Point(rect.maxX.toInt(), maxY))
  }
}