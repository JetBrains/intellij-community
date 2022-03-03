// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Insets
import javax.swing.border.Border

abstract class JBTabsBorder(val tabs: JBTabsImpl) : Border {
  val thickness: Int
    get() = if (tabs.tabPainter == null) JBUI.scale(1) else tabs.tabPainter.getTabTheme().topBorderThickness

  override fun getBorderInsets(c: Component?): Insets = JBInsets.emptyInsets()

  override fun isBorderOpaque(): Boolean {
    return true
  }

  open val effectiveBorder: Insets
    get() = JBInsets.emptyInsets()

}