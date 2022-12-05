// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.collaboration.ui.JPanelWithBackground
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.geom.RoundRectangle2D

@ApiStatus.Internal
class RoundedPanel(layout: LayoutManager?, private val arc: Int = 8) : JPanelWithBackground(layout) {

  init {
    isOpaque = false
    cursor = Cursor.getDefaultCursor()
    border = RoundedLineBorder(DEFAULT_BORDER_COLOR, arc + 2)
  }

  override fun setOpaque(isOpaque: Boolean) {} // Disable opaque

  override fun paintChildren(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      g2.clip(getShape())
      super.paintChildren(g2)
    }
    finally {
      g2.dispose()
    }
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      GraphicsUtil.setupRoundedBorderAntialiasing(g2)
      g2.clip(getShape())
      super.paintComponent(g2)
    }
    finally {
      g2.dispose()
    }
  }

  private fun getShape(): Shape {
    val rect = Rectangle(size)
    JBInsets.removeFrom(rect, insets)
    // 2.25 scale is a @#$!% so we adjust sizes manually
    return RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(),
                                  rect.width.toFloat(), rect.height.toFloat(),
                                  arc.toFloat(), arc.toFloat())
  }

  companion object {
    private val DEFAULT_BORDER_COLOR = JBColor.namedColor("Review.ChatItem.BubblePanel.Border",
                                                          JBColor.namedColor("EditorTabs.underTabsBorderColor",
                                                                             JBColor.border()))
  }
}
