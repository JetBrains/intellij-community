// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar.components

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.util.text.StringUtil
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JLabel

open class TrimmedMiddleLabel : JLabel() {
  private val magicConst = 5

  override fun paintComponent(g: Graphics) {
    val fm = getFontMetrics(font)
    g as Graphics2D
    UISettings.setupAntialiasing(g)
    val textW = fm.stringWidth(text)
    var availableWidth = width - insets.right - insets.left
    var x = insets.left
    icon?.let {
      x += iconTextGap + it.iconWidth
      availableWidth -= x
    }

    val ellipsisWidth = fm.stringWidth(StringUtil.ELLIPSIS)
    if (availableWidth <= 0 || textW <= availableWidth + (icon?.let { 0 } ?: magicConst) || width < ellipsisWidth) {
      super.paintComponent(g)
    }
    else {
      var offsetY = fm.ascent
      icon?.let {
        icon.paintIcon(this, g, insets.left, 0)
        offsetY += (it.iconWidth / 2) - fm.height/2
      }

      val charArray = text.toCharArray()
      val stringLength = charArray.size
      var w = 0
      val avW = availableWidth - ellipsisWidth

      for (nChars in 0 until stringLength) {
        w += fm.charWidth(charArray[nChars])
        if (w > avW) {
          val ind = nChars - 1
          if(ind > 0) {
            g.drawString(StringUtil.trimMiddle(text, ind), x, offsetY)
          } else super.paintComponent(g)
          return
        }
      }
    }
  }
}