// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.ui.tabs.JBTabsBorder
import java.awt.*

open class JBDefaultTabsBorder(tabs: JBTabsImpl) : JBTabsBorder(tabs) {
  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    if (tabs.isEmptyVisible) return
    g as Graphics2D


    if (JBTabsImpl.NEW_TABS) {
      val borderLines = tabs.lastLayoutPass.extraBorderLines ?: return
      for (borderLine in borderLines) {
        tabs.tabPainter.paintBorderLine(g, thickness, borderLine.from(), borderLine.to())
      }
    }
    else {
      val rect = Rectangle(x, y, width, height)
      val firstLabel = tabs.myInfo2Label[tabs.visibleInfos[0]] ?: return
      val maxY = firstLabel.bounds.maxY.toInt() - thickness
      tabs.tabPainter.paintBorderLine(g, thickness, Point(rect.x, maxY), Point(rect.maxX.toInt(), maxY))
    }
  }
}