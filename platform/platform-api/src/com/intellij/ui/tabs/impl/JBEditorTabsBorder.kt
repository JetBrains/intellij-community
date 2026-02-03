// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ui.tabs.impl

import com.intellij.openapi.rd.paint2DLine
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.tabs.JBTabsBorder
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.util.animation.Easing
import com.intellij.util.animation.JBAnimator
import com.intellij.util.animation.animation
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.*

@Internal
class JBEditorTabsBorder(tabs: JBTabsImpl) : JBTabsBorder(tabs) {
  private val animator = JBAnimator()
  private var start: Int = -1
  private var end: Int = -1
  private var animationId = -1L

  init {
    tabs.addListener(object : TabsListener {
      override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
        val from = bounds(oldSelection) ?: return
        val to = bounds(newSelection) ?: return
        if (from.width == 0 || to.width == 0) return //tab was added or removed. See IDEA-331744
        val dur = 100
        val del = 50
        val s1 = from.x
        val s2 = to.x
        val d1 = if (s1 > s2) 0 else del
        val e1 = from.x + from.width
        val e2 = to.x + to.width
        val d2 = if (e1 > e2) del else 0
        animationId = animator.animate(
          animation(s1, s2) {
            start = it
            tabs.component.repaint()
          }.apply {
            duration = dur - d1
            delay = d1
            easing = if (d1 != 0) Easing.EASE_OUT else Easing.LINEAR
          },
          animation(e1, e2) {
            end = it
            tabs.component.repaint()
          }.apply {
            duration = dur - d2
            delay = d2
            easing = if (d2 != 0) Easing.EASE_OUT else Easing.LINEAR
          }
        )
      }

      private fun bounds(tabInfo: TabInfo?): Rectangle? {
        return tabs.getTabLabel(tabInfo ?: return null)?.bounds
      }
    })
  }

  override val effectiveBorder: Insets
    get() = Insets(thickness, 0, 0, 0)

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    g as Graphics2D

    if (ExperimentalUI.isNewUI()) {
      g.paint2DLine(Point(x, y), Point(x + width, y), LinePainter2D.StrokeType.INSIDE, thickness.toDouble(),
                    JBUI.CurrentTheme.MainToolbar.borderColor())
    }
    else {
      tabs.tabPainter.paintBorderLine(g, thickness, Point(x, y), Point(x + width, y))
    }

    if (tabs.isEmptyVisible || tabs.isHideTabs) {
      return
    }

    val firstLabel = tabs.getTabLabel(tabs.getVisibleInfos().first()) ?: return

    when (tabs.tabsPosition) {
      JBTabsPosition.top -> {
        val startY = firstLabel.y - if (tabs.tabsPosition == JBTabsPosition.bottom) 0 else thickness
        val startRow = if (ExperimentalUI.isNewUI()) 1 else 0
        val lastRow = tabs.lastLayoutPass!!.rowCount
        for (eachRow in startRow until lastRow) {
          val yl = (eachRow * tabs.headerFitSize!!.height) + startY
          tabs.tabPainter.paintBorderLine(g, thickness, Point(x, yl), Point(x + width, yl))
        }
        if (!ExperimentalUI.isNewUI() || (tabs as? JBEditorTabs)?.shouldPaintBottomBorder() == true) {
          val yl = lastRow * tabs.headerFitSize!!.height + startY
          tabs.tabPainter.paintBorderLine(g, thickness, Point(x, yl), Point(x + width, yl))
        }
      }
      JBTabsPosition.bottom -> {
        val rowCount = tabs.lastLayoutPass!!.rowCount
        for (rowInd in 0 until rowCount) {
          val curY = height - (rowInd + 1) * tabs.headerFitSize!!.height
          tabs.tabPainter.paintBorderLine(g, thickness, Point(x, curY), Point(x + width, curY))
        }
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

    if (hasAnimation()) {
      tabs.tabPainter.paintUnderline(tabs.tabsPosition, calcRectangle() ?: return, thickness, g, tabs.isActiveTabs(tabs.selectedInfo))
    }
    else {
      val selectedLabel = tabs.selectedLabel ?: return
      tabs.tabPainter.paintUnderline(tabs.tabsPosition, selectedLabel.bounds, thickness, g, tabs.isActiveTabs(tabs.selectedInfo))
    }
  }

  private fun calcRectangle(): Rectangle? {
    val selectedLabel = tabs.selectedLabel ?: return null
    if (animator.isRunning(animationId)) {
      return Rectangle(start, selectedLabel.y, end - start, selectedLabel.height)
    }
    return selectedLabel.bounds
  }

  companion object {
    internal fun hasAnimation(): Boolean = Registry.`is`("ide.editor.tab.selection.animation", false)
  }
}