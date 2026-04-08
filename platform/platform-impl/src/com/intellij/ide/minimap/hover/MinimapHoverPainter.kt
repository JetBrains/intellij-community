// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the hover frame on the minimap. The components are:
 *
 * =========== - top bar, placed in the line where the structure element is defined
 * |
 * |
 * | - thin vertical line till the end of the element
 * |
 * |= - a little hook at the very bottom
 */
class MinimapHoverPainter {
  fun paint(graphics: Graphics2D, rect: Rectangle, lineHeight: Int, color: Color) {
    val topHeight = lineHeight.coerceIn(1, rect.height)
    graphics.color = color
    // top bar, todo: consider making it shorter, like the structure element itself
    graphics.fillRect(rect.x, rect.y, rect.width, topHeight)

    val hookHeight = min(rect.height - topHeight, HOOK_HEIGHT)
    val lineHeightPx = (rect.height - topHeight - hookHeight).coerceAtLeast(0)

    // the vertical line
    if (lineHeightPx > 0) {
      graphics.fillRect(rect.x, rect.y + topHeight, VERTICAL_LINE_WIDTH, lineHeightPx)
    }

    // the hook
    if (hookHeight > 0) {
      val hookWidth = min(rect.width, max(MIN_HOOK_WIDTH, topHeight * 2))
      val hookY = rect.y + rect.height - hookHeight
      graphics.fillRect(rect.x, hookY, hookWidth, hookHeight)
    }
  }

  companion object {
    private val VERTICAL_LINE_WIDTH = JBUI.scale(1)
    private val HOOK_HEIGHT = JBUI.scale(2)
    private val MIN_HOOK_WIDTH = JBUI.scale(6)
  }
}
