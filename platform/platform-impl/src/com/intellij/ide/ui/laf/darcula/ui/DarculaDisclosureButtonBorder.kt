// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.DrawUtil
import com.intellij.ui.components.DisclosureButton
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.border.Border
import javax.swing.plaf.UIResource

@ApiStatus.Internal
class DarculaDisclosureButtonBorder : Border, UIResource {

  override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
    if (g == null || c !is DisclosureButton) {
      return
    }

    val g2 = g.create() as Graphics2D

    try {
      DrawUtil.setupRenderingHints(g2)

      val r = Rectangle(x, y, width, height)
      JBInsets.removeFrom(r, getBorderInsets(c))

      if (c.hasFocus()) {
        DarculaNewUIUtil.paintComponentBorder(g2, r, DarculaUIUtil.Outline.focus, true, true, arc = c.arc.toFloat())
      }
    }
    finally {
      g2.dispose()
    }
  }

  override fun getBorderInsets(c: Component?): Insets {
    return JBInsets(3).asUIResource()
  }

  override fun isBorderOpaque(): Boolean {
    return false
  }
}
