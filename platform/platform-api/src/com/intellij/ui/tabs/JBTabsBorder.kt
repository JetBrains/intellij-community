// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs

import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Insets
import javax.swing.border.Border

abstract class JBTabsBorder(val tabs: JBTabsImpl) : Border {
  var thickness: Int = JBUI.scale(1)

  override fun getBorderInsets(c: Component?): Insets = JBUI.emptyInsets()

  override fun isBorderOpaque(): Boolean {
    return true
  }

  open val effectiveBorder: Insets
    get() = JBUI.emptyInsets()

}