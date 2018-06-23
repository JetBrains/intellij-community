// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij

import com.intellij.ide.ui.laf.darcula.ui.DarculaOptionButtonUI
import com.intellij.ide.ui.laf.intellij.MacIntelliJTextBorder.LW
import com.intellij.util.ui.JBUI.scale
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent

// TODO replace arrow with specific icon when it's ready
class MacIntelliJOptionButtonUI : DarculaOptionButtonUI() {
  override val arrowButtonPreferredSize: Dimension get() = Dimension(scale(21), optionButton.preferredSize.height)

  override val showPopupXOffset: Int = scale(3)

  override fun paintSeparator(g: Graphics2D, c: JComponent) {
    val insets = mainButton.insets
    val lw = LW(g)

    g.paint = MacIntelliJButtonUI.getBorderPaint(c)
    g.fill(Rectangle2D.Float(mainButton.width.toFloat(), insets.top + lw, lw, mainButton.height - (insets.top + insets.bottom + 2 * lw)))
  }

  companion object {
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun createUI(c: JComponent): MacIntelliJOptionButtonUI = MacIntelliJOptionButtonUI()
  }
}