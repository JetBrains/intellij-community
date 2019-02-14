// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs

import java.awt.Insets
import javax.swing.border.Border

interface JBTabsBackgroundAndBorder: Border {
  var thickness: Int
  val effectiveBorder: Insets

  @JvmDefault
  override fun isBorderOpaque(): Boolean {
    return true
  }
}