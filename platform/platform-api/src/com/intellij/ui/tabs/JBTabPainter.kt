// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs

import com.intellij.ui.tabs.impl.JBDefaultTabPainter
import com.intellij.ui.tabs.impl.JBEditorTabPainter
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle

interface JBTabPainter {
  companion object {
    val EDITOR = JBEditorTabPainter()
    val TOOL_WINDOW: JBTabPainter = JBDefaultTabPainter(TabTheme.TOOL_WINDOW)
    val DEFAULT: JBTabPainter = JBDefaultTabPainter()
  }

  fun getBackgroundColor(): Color

  fun paintBorderLine(g: Graphics2D, thickness: Int, from: Point, to: Point)

  fun fillBackground(g: Graphics2D, rect: Rectangle)

  fun paintTab(position: JBTabsPosition,
                        g: Graphics2D,
                        bounds: Rectangle,
                        borderThickness: Int,
                        tabColor: Color?,
                        hovered: Boolean)

  fun paintSelectedTab(position: JBTabsPosition,
                                g: Graphics2D,
                                rect: Rectangle,
                                tabColor: Color?,
                                active: Boolean,
                                hovered: Boolean)

}