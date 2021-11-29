// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar.components

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.util.text.StringUtil
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JLabel

open class TrimmedMiddleLabel : JLabel() {
  override fun paintComponent(g: Graphics) {
    val fm = g.getFontMetrics(g.font)
    g as Graphics2D
    UISettings.setupAntialiasing(g)
    val textW = fm.stringWidth(text)
    var availableWidth = width - insets.right - insets.left
    var x = insets.left
    icon?.let {
      x += iconTextGap + it.iconWidth
    }

    if (textW < availableWidth) {
      super.paintComponent(g)
    }
    else {
      icon?.let {
        icon.paintIcon(this, g, insets.left, 0)
      }
      availableWidth -= (x + fm.stringWidth(StringUtil.ELLIPSIS))

      val charArray = text.toCharArray()
      val stringLength = charArray.size
      var w = 0
      for (nChars in 0 until stringLength) {
        w += fm.charWidth(charArray[nChars])
        if (w > availableWidth) {
          g.drawString(StringUtil.trimMiddle(text, nChars - 1), x, fm.ascent)
          return
        }
      }
    }
  }
}