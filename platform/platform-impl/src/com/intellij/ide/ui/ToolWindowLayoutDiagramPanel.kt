// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JPanel

/**
 * A small mockup of the IDE main window illustrating how tool window layout
 * settings affect the placement of tool windows, tool window bars and the editor.
 */
internal class ToolWindowLayoutDiagramPanel : JPanel() {

  var wideScreenSupport: Boolean = UISettings.getInstance().wideScreenSupport
    set(value) { if (field != value) { field = value; repaint() } }

  var leftHorizontalSplit: Boolean = UISettings.getInstance().leftHorizontalSplit
    set(value) { if (field != value) { field = value; repaint() } }

  var rightHorizontalSplit: Boolean = UISettings.getInstance().rightHorizontalSplit
    set(value) { if (field != value) { field = value; repaint() } }

  var showToolWindowBars: Boolean = !UISettings.getInstance().hideToolStripes
    set(value) { if (field != value) { field = value; repaint() } }

  var showToolWindowNames: Boolean = UISettings.getInstance().showToolWindowsNames
    set(value) { if (field != value) { field = value; repaint() } }

  private val frameBorderColor = JBColor(Color(0xB6B6B6), Color(0x1B1B1B))
  private val titleBarTopColor = JBColor(Color(0xE3E3E3), Color(0x4B4257))
  private val titleBarBottomColor = JBColor(Color(0xCFCFCF), Color(0x2E2933))
  private val backgroundColor = JBColor(Color(0xC8C8C8), Color(0x1F1F1F))
  private val toolWindowColor = JBColor(Color(0xEDEDED), Color(0x2D2D2D))
  private val editorColor = JBColor(Color(0xFFFFFF), Color(0x191919))
  private val barColor = JBColor(Color(0xD8D8D8), Color(0x252525))
  private val buttonColor = JBColor(Color(0x9C9C9C), Color(0x6E6E6E))
  private val nameLineColor = JBColor(Color(0xB8B8B8), Color(0x4D4D4D))
  private val closeDotColor = JBColor(Color(0xFF5F57), Color(0xFF5F57))
  private val minimizeDotColor = JBColor(Color(0xFEBC2E), Color(0xFEBC2E))
  private val expandDotColor = JBColor(Color(0x28C840), Color(0x28C840))

  init {
    preferredSize = JBUI.size(270, 170)
    minimumSize = JBUI.size(240, 140)
    maximumSize = Dimension(JBUI.scale(320), JBUI.scale(220))
    isOpaque = false
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      val w = width
      val h = height
      val arc = JBUI.scale(8)

      g2.color = backgroundColor
      g2.fillRoundRect(0, 0, w, h, arc, arc)

      val titleH = JBUI.scale(11)
      drawTitleBar(g2, w, titleH, arc)

      g2.color = frameBorderColor
      g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc)

      val padX = JBUI.scale(3)
      val padY = JBUI.scale(2)
      val contentX = padX
      val contentY = titleH + padY
      val contentW = w - 2 * padX
      val contentH = h - contentY - padY
      val gap = JBUI.scale(1)

      val barWidth = if (showToolWindowBars) JBUI.scale(8) else 0
      val barGap = if (showToolWindowBars) gap else 0

      if (showToolWindowBars) {
        drawToolWindowBar(g2, contentX, contentY, barWidth, contentH)
        drawToolWindowBar(g2, contentX + contentW - barWidth, contentY, barWidth, contentH)
      }

      val mainX = contentX + barWidth + barGap
      val mainW = contentW - 2 * (barWidth + barGap)
      val mainY = contentY
      val mainH = contentH

      val sideW = (mainW * 0.22).toInt().coerceAtLeast(JBUI.scale(14))
      val bottomH = (mainH * 0.28).toInt().coerceAtLeast(JBUI.scale(18))

      val leftX = mainX
      val rightX = mainX + mainW - sideW
      val centerX = leftX + sideW + gap
      val centerW = rightX - centerX - gap

      val sideHeight: Int
      val bottomStartX: Int
      val bottomW: Int

      if (wideScreenSupport) {
        sideHeight = mainH
        bottomStartX = centerX
        bottomW = centerW
      }
      else {
        sideHeight = mainH - bottomH - gap
        bottomStartX = leftX
        bottomW = mainW
      }
      val bottomY = mainY + mainH - bottomH

      drawSideRegion(g2, leftX, mainY, sideW, sideHeight, leftHorizontalSplit, gap)
      drawSideRegion(g2, rightX, mainY, sideW, sideHeight, rightHorizontalSplit, gap)
      drawToolWindow(g2, bottomStartX, bottomY, bottomW, bottomH)

      val editorH = if (wideScreenSupport) mainH - bottomH - gap else sideHeight
      drawEditor(g2, centerX, mainY, centerW, editorH)
    }
    finally {
      g2.dispose()
    }
  }

  private fun drawTitleBar(g2: Graphics2D, w: Int, titleH: Int, arc: Int) {
    val titleG = g2.create() as Graphics2D
    try {
      titleG.clipRect(0, 0, w, titleH)
      titleG.paint = GradientPaint(0f, 0f, titleBarTopColor, w.toFloat(), titleH.toFloat(), titleBarBottomColor)
      titleG.fillRoundRect(0, 0, w, titleH + arc, arc, arc)
    }
    finally {
      titleG.dispose()
    }

    val dot = JBUI.scale(3)
    val dotY = (titleH - dot) / 2
    val dotGap = JBUI.scale(2)
    var dotX = JBUI.scale(5)
    g2.color = closeDotColor
    g2.fillOval(dotX, dotY, dot, dot)
    dotX += dot + dotGap
    g2.color = minimizeDotColor
    g2.fillOval(dotX, dotY, dot, dot)
    dotX += dot + dotGap
    g2.color = expandDotColor
    g2.fillOval(dotX, dotY, dot, dot)
  }

  private fun drawToolWindowBar(g2: Graphics2D, x: Int, y: Int, w: Int, h: Int) {
    g2.color = barColor
    g2.fillRect(x, y, w, h)

    val btnSize = JBUI.scale(4)
    val nameH = JBUI.scale(1)
    val nameW = JBUI.scale(5)
    val gapInItem = JBUI.scale(1)
    val gapBetweenItems = JBUI.scale(2)
    val itemH = btnSize + (if (showToolWindowNames) gapInItem + nameH else 0)
    val rowHeight = itemH + gapBetweenItems
    val btnX = x + (w - btnSize) / 2
    val nameX = x + (w - nameW) / 2
    val maxTopButtons = 3
    val maxBottomButtons = 2

    val available = h - JBUI.scale(4)
    val capacity = (available + gapBetweenItems) / rowHeight
    val topCount = maxTopButtons.coerceAtMost(capacity)
    val bottomCount = maxBottomButtons.coerceAtMost(capacity - topCount)

    val topStartY = y + JBUI.scale(2)
    for (i in 0 until topCount) {
      drawBarItem(g2, btnX, topStartY + i * rowHeight, btnSize, nameX, nameW, nameH, gapInItem)
    }

    val bottomStartY = y + h - JBUI.scale(2) - itemH
    for (i in 0 until bottomCount) {
      drawBarItem(g2, btnX, bottomStartY - i * rowHeight, btnSize, nameX, nameW, nameH, gapInItem)
    }
  }

  private fun drawBarItem(g2: Graphics2D, btnX: Int, y: Int, btnSize: Int, nameX: Int, nameW: Int, nameH: Int, gapInItem: Int) {
    g2.color = buttonColor
    g2.fillRect(btnX, y, btnSize, btnSize)
    if (showToolWindowNames) {
      g2.color = nameLineColor
      g2.fillRect(nameX, y + btnSize + gapInItem, nameW, nameH)
    }
  }

  private fun drawSideRegion(g2: Graphics2D, x: Int, y: Int, w: Int, h: Int, split: Boolean, gap: Int) {
    if (!split) {
      drawToolWindow(g2, x, y, w, h)
      return
    }
    val firstW = (w - gap) / 2
    val secondW = w - gap - firstW
    drawToolWindow(g2, x, y, firstW, h)
    drawToolWindow(g2, x + firstW + gap, y, secondW, h)
  }

  private fun drawToolWindow(g2: Graphics2D, x: Int, y: Int, w: Int, h: Int) {
    val arc = JBUI.scale(3)
    g2.color = toolWindowColor
    g2.fillRoundRect(x, y, w, h, arc, arc)
  }

  private fun drawEditor(g2: Graphics2D, x: Int, y: Int, w: Int, h: Int) {
    val arc = JBUI.scale(3)
    g2.color = editorColor
    g2.fillRoundRect(x, y, w, h, arc, arc)
  }
}
