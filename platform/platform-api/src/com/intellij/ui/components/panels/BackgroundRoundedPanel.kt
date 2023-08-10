// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.panels

import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JPanel

open class BackgroundRoundedPanel(private val arcSize: Int) : JPanel() {

  override fun paintComponent(g: Graphics?) {
    if (!isOpaque) {
      return
    }
    //java.awt.Graphics2D#clip provides worse painting result than explicit rounded rectangle painting
    (g as? Graphics2D)?.let { g2 ->
      g2.color = background
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
      g2.fillRoundRect(0, 0, width, height, arcSize, arcSize)
    }
  }
}
