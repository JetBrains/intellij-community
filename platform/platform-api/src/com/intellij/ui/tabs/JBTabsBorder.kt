// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Insets
import javax.swing.border.Border

@ApiStatus.Internal
abstract class JBTabsBorder(@JvmField protected val tabs: JBTabsImpl) : Border {
  val thickness: Int
    get() = tabs.tabPainter.getTabTheme().topBorderThickness

  override fun getBorderInsets(c: Component?): Insets = JBInsets.emptyInsets()

  override fun isBorderOpaque(): Boolean = true

  open val effectiveBorder: Insets
    get() = JBInsets.emptyInsets()
}