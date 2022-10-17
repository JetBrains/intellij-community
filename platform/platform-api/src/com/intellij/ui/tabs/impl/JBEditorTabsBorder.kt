// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.awt.*

class JBEditorTabsBorder(tabs: JBTabsImpl) : JBTabsBorder(tabs) {

  private val animator = JBAnimator()
  private var start: Int = -1
  private var end: Int = -1
  private var animationId = -1L

  init {
    tabs.addListener(object : TabsListener {
      override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
        val from = bounds(oldSelection)
        val to = bounds(newSelection)
        if (from != null && to != null) {
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
      }

      private fun bounds(tabInfo: TabInfo?): Rectangle? {
        return tabs.myInfo2Label[tabInfo ?: return null]?.bounds
      }
    })
  }

  override val effectiveBorder: Insets
    get() = Insets(thickness, 0, 0, 0)

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    g as Graphics2D

    if (ExperimentalUI.isNewUI()) {
      g.paint2DLine(Point(x, y), Point(x + width, y), LinePainter2D.StrokeType.INSIDE,
                    thickness.toDouble(), JBUI.CurrentTheme.EditorTabs.borderColor())
    }
    else tabs.tabPainter.paintBorderLine(g, thickness, Point(x, y), Point(x + width, y))

    if (tabs.isEmptyVisible || tabs.isHideTabs) return

    val myInfo2Label = tabs.myInfo2Label
    val firstLabel = myInfo2Label[tabs.visibleInfos[0]] ?: return

    val startY = firstLabel.y - if (tabs.position == JBTabsPosition.bottom) 0 else thickness

    when (tabs.position) {
      JBTabsPosition.top -> {
        val startRow = if (ExperimentalUI.isNewUI()) 1 else 0
        val lastRow = tabs.lastLayoutPass.rowCount
        for (eachRow in startRow until lastRow) {
          val yl = (eachRow * tabs.myHeaderFitSize.height) + startY
          tabs.tabPainter.paintBorderLine(g, thickness, Point(x, yl), Point(x + width, yl))
        }
        if (!ExperimentalUI.isNewUI() || (tabs as? JBEditorTabs)?.shouldPaintBottomBorder() == true) {
          val yl = lastRow * tabs.myHeaderFitSize.height + startY
          tabs.tabPainter.paintBorderLine(g, thickness, Point(x, yl), Point(x + width, yl))
        }
      }
      JBTabsPosition.bottom -> {
        tabs.tabPainter.paintBorderLine(g, thickness, Point(x, startY), Point(x + width, startY))
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
      tabs.tabPainter.paintUnderline(tabs.position, calcRectangle() ?: return, thickness, g, tabs.isActiveTabs(tabs.selectedInfo))
    } else {
      val selectedLabel = tabs.selectedLabel ?: return
      tabs.tabPainter.paintUnderline(tabs.position, selectedLabel.bounds, thickness, g, tabs.isActiveTabs(tabs.selectedInfo))
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
    fun hasAnimation() = Registry.`is`("ide.editor.tab.selection.animation", false)
  }
}