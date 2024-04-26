// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.panels

import com.intellij.util.ui.GraphicsUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LayoutManager
import javax.swing.JPanel

//TODO: provide an external API
@ApiStatus.Internal
class BackgroundRoundedPanel(private val arcSize: Int, layoutManager: LayoutManager? = null) : JPanel(layoutManager) {
  init {
    isOpaque = false
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    //java.awt.Graphics2D#clip provides worse painting result than explicit rounded rectangle painting
    val g2 = g.create() as Graphics2D
    try {
      GraphicsUtil.setupAAPainting(g2)
      g2.color = background
      g2.fillRoundRect(0, 0, width, height, arcSize, arcSize)
    }
    finally {
      g2.dispose()
    }
  }
}
