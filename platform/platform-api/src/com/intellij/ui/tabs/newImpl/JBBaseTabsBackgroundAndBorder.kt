// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl

import com.intellij.ui.tabs.JBTabsBackgroundAndBorder
import com.intellij.util.ui.JBUI
import java.awt.*

abstract class JBBaseTabsBackgroundAndBorder(val tabs: JBTabsImpl) : JBTabsBackgroundAndBorder {
  override var thickness: Int = 1

  override fun getBorderInsets(c: Component?): Insets = JBUI.emptyInsets()

  override val effectiveBorder: Insets
    get() = JBUI.emptyInsets()

  protected fun paintBackground(g: Graphics2D, rect: Rectangle) {
    tabs.getTabPainter().fillBackground(g, rect)
  }
}