// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo

/** Narrows the theme list to the themes of one brightness. The Light/Dark switch of the theme chooser sets it. */
internal enum class ThemeBrightnessFilter {
  LIGHT,
  DARK;

  fun accepts(theme: UIThemeLookAndFeelInfo): Boolean = (this == DARK) == theme.isDark

  companion object {
    fun of(isDark: Boolean): ThemeBrightnessFilter = if (isDark) DARK else LIGHT
  }
}
