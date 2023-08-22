// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.experimental.meetNewUi

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.paint.LinePainter2D.StrokeType
import com.intellij.ui.paint.RectanglePainter2D
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.SwingUtilities

internal class MeetNewUiButton(text: @NlsContexts.Button String? = null,
                               private val icon: Icon? = null,
                               private val iconSelected: Icon?) : JLabel(text, icon, LEFT) {

  var selected: Boolean = false
    set(value) {
      if (field != value) {
        setIcon(if (value) iconSelected else icon)
        field = value
        repaint()
      }
    }

  var selectionArc: Int = 6
    set(value) {
      if (field != value) {
        field = value
        repaint()
      }
    }

  private val selectedBorderColor = JBUI.CurrentTheme.Focus.focusColor()
  private val selectedBorderSize = 2
  private val unselectedBorderColor = JBUI.CurrentTheme.Button.buttonOutlineColorStart(false)
  private val unselectedBorderSize = 1
  private val clickListeners = mutableListOf<Runnable>()

  private var fireClicked = false

  init {
    border = JBUI.Borders.empty(8, if (icon == null) 12 else 8, 8, 12)
    iconTextGap = JBUI.scale(5)

    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent?) {
        fireClicked = true
      }

      override fun mouseReleased(e: MouseEvent?) {
        if (fireClicked && SwingUtilities.isLeftMouseButton(e)) {
          fireClicked = false

          for (listener in clickListeners) {
            listener.run()
          }
        }
      }

      override fun mouseExited(e: MouseEvent?) {
        fireClicked = false
      }
    })
  }

  override fun paintComponent(g: Graphics?) {
    g as Graphics2D

    g.paint = UIUtil.getGradientPaint(0f, 0f, JBUI.CurrentTheme.Button.buttonColorStart(),
                                      0f, height.toFloat(), JBUI.CurrentTheme.Button.buttonColorEnd())
    RectanglePainter2D.FILL.paint(g, 0.0, 0.0, width.toDouble(), height.toDouble(), selectionArc.toDouble(),
                                  StrokeType.INSIDE, 0.0, RenderingHints.VALUE_ANTIALIAS_ON)

    super.paintComponent(g)

    val borderSize: Int
    if (selected) {
      g.color = selectedBorderColor
      borderSize = selectedBorderSize
    }
    else {
      g.color = unselectedBorderColor
      borderSize = unselectedBorderSize
    }

    RectanglePainter2D.DRAW.paint(g, 0.0, 0.0, width.toDouble(), height.toDouble(), selectionArc.toDouble(),
                                  StrokeType.INSIDE, JBUI.scale(borderSize).toDouble(), RenderingHints.VALUE_ANTIALIAS_ON)
  }

  fun addClickListener(listener: Runnable) {
    clickListeners += listener
  }
}