// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.ui.tabs.JBTabsBorder
import com.intellij.ui.tabs.JBTabsPosition
import java.awt.*

class JBEditorTabsBorder(tabs: JBTabsImpl) : JBTabsBorder(tabs) {

  override val effectiveBorder: Insets
    get() = Insets(thickness, 0, 0, 0)

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    g as Graphics2D

    tabs.tabPainter.paintBorderLine(g, thickness, Point(x, y), Point(x + width, y))
    if(tabs.isEmptyVisible || tabs.isHideTabs) return

    if (JBTabsImpl.NEW_TABS) {
      val borderLines = tabs.lastLayoutPass.extraBorderLines ?: return
      for (borderLine in borderLines) {
        tabs.tabPainter.paintBorderLine(g, thickness, borderLine.from(), borderLine.to())
      }
    } else {
      val myInfo2Label = tabs.myInfo2Label
      val firstLabel = myInfo2Label[tabs.visibleInfos[0]] ?: return

      val startY = firstLabel.y - if (tabs.position == JBTabsPosition.bottom) 0 else thickness

      when(tabs.position) {
        JBTabsPosition.top -> {
          for (eachRow in 0..tabs.lastLayoutPass.rowCount) {
            val yl = (eachRow * tabs.myHeaderFitSize.height) + startY
            tabs.tabPainter.paintBorderLine(g, thickness, Point(x, yl), Point(x + width, yl))
          }
        }
        JBTabsPosition.bottom -> {
          tabs.tabPainter.paintBorderLine(g, thickness, Point(x, startY), Point(x + width, startY))
          tabs.tabPainter.paintBorderLine(g, thickness, Point(x, y), Point(x + width, y))
        }
        JBTabsPosition.right -> {
          val lx = firstLabel.x
          tabs.tabPainter.paintBorderLine(g, thickness, Point(lx, y), Point(lx, y + height))
        }

        JBTabsPosition.left -> {
          val bounds = firstLabel.bounds
          val i = bounds.x + bounds.width - thickness
          tabs.tabPainter.paintBorderLine(g, thickness, Point(i, y), Point(i, y + height))
        }
      }
    }

    val selectedLabel = tabs.selectedLabel ?: return
    tabs.tabPainter.paintUnderline(tabs.position, selectedLabel.bounds, thickness, g, tabs.isActiveTabs(tabs.selectedInfo))
  }
}