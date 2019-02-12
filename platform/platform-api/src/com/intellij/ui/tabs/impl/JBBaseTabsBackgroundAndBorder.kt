// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.ui.tabs.JBTabsBackgroundAndBorder
import com.intellij.util.ui.JBUI
import java.awt.*

open abstract class JBBaseTabsBackgroundAndBorder(val tabs: JBTabsImpl) : JBTabsBackgroundAndBorder {
  private var thickness: Int = 1

  override fun setThickness(value: Int) {
    thickness = value
  }

  override fun getThickness(): Int = thickness

  override fun getBorderInsets(c: Component?): Insets = JBUI.emptyInsets()

  override fun getEffectiveBorder(): Insets = JBUI.emptyInsets()

  protected fun paintBackground(g: Graphics2D, rect: Rectangle) {
    tabs.getTabPainter().fillBackground(g, rect)
  }
}