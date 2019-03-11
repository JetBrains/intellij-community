// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs

import com.intellij.ui.tabs.newImpl.JBTabsImpl
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.border.Border

abstract class JBTabsBackgroundAndBorder(val tabs: JBTabsImpl) : Border {
  var thickness: Int = 1

  override fun getBorderInsets(c: Component?): Insets = JBUI.emptyInsets()

  override fun isBorderOpaque(): Boolean {
    return true
  }

  open val effectiveBorder: Insets
    get() = JBUI.emptyInsets()

  protected fun paintBackground(g: Graphics2D, rect: Rectangle) {
    tabs.tabPainter.fillBackground(g, rect)
  }
}