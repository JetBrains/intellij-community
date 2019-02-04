// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime

import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.ui.border.CustomLineBorder
import com.intellij.util.ui.JBUI
import java.awt.GridBagLayout
import javax.swing.JPanel
import javax.swing.border.CompoundBorder

class ActionPanel : JPanel(GridBagLayout()) {
  init {
    val line = CustomLineBorder(OnePixelDivider.BACKGROUND, 1, 0, 0, 0)
    setBorder(CompoundBorder(line, JBUI.Borders.empty(8, 12)))
  }

  override fun addNotify() {
    super.addNotify()
    this.minimumSize = preferredSize
  }
}
