// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.MacUIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.border.Border
import javax.swing.plaf.UIResource

@ApiStatus.Internal
class DarculaDisclosureButtonBorder : Border, UIResource {

  override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
    if (g == null || c !is DarculaDisclosureButton) {
      return
    }

    val g2 = g.create() as Graphics2D

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)

      val r = Rectangle(x, y, width, height)
      JBInsets.removeFrom(r, getBorderInsets(c))

      if (c.hasFocus()) {
        DarculaNewUIUtil.paintComponentBorder(g2, r, DarculaUIUtil.Outline.focus, true, true, arc = DarculaDisclosureButtonUI.ARC.float)
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
