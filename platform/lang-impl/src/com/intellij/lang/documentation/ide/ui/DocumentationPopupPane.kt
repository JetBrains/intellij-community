// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.ui

import com.intellij.ui.WidthBasedLayout
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class DocumentationPopupPane(
  private val variableHeightComponent: JComponent,
) : JPanel(BorderLayout()), WidthBasedLayout {

  override fun getPreferredWidth(): Int = preferredSize.width

  override fun getPreferredHeight(width: Int): Int {
    UIUtil.putClientProperty(variableHeightComponent, FORCED_WIDTH, width)
    try {
      return preferredSize.height
    }
    finally {
      UIUtil.putClientProperty(variableHeightComponent, FORCED_WIDTH, null)
    }
  }
}
