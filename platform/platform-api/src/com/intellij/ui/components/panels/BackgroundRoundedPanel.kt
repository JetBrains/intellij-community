// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.panels

import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LayoutManager
import javax.swing.JPanel
import kotlin.properties.Delegates.observable

//TODO: provide an external API
@ApiStatus.Internal
open class BackgroundRoundedPanel(private val arcSize: Int, layoutManager: LayoutManager? = null) : JPanel(layoutManager) {
  var fillBorder: Boolean by observable(true) { _, _, _ -> repaint() }

  init {
    super.isOpaque = false
  }

  final override fun setOpaque(isOpaque: Boolean) {
    // this panel is always non-opaque because of rounding
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    //java.awt.Graphics2D#clip provides worse painting result than explicit rounded rectangle painting
    val g2 = g.create() as Graphics2D
    try {
      val rect = bounds
      if (!fillBorder) {
        JBInsets.removeFrom(rect, insets)
      }
      GraphicsUtil.setupAAPainting(g2)
      g2.color = background
      g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, arcSize, arcSize)
    }
    finally {
      g2.dispose()
    }
  }
}
