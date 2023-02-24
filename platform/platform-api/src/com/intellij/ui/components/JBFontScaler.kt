// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ide.ui.UISettingsUtils
import com.intellij.util.ui.JBFont
import java.awt.Font

class JBFontScaler(private val origFont: Font) {
  private val originalDefaultSize = JBFont.label().size
  private val currentDefaultSize get() = JBFont.label().size

  fun scaledFont(): Font {
    return if (originalDefaultSize == currentDefaultSize) origFont
    else {
      val newSize =
        if (origFont.size == originalDefaultSize) JBFont.label().size.toFloat()
        else UISettingsUtils.scaleFontSize(origFont.size.toFloat(), currentDefaultSize.toFloat() / originalDefaultSize)

      if (origFont is JBFont) origFont.deriveFont(newSize)
      else JBFont.create(origFont, false).deriveFont(newSize)
    }
  }
}