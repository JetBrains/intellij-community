// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@ApiStatus.Internal
interface UIThemeCustomizer {
  fun createColorCustomizer(themeName: String): Map<String, Color>
  fun createNamedColorCustomizer(themeName: String): Map<String, String>
  fun createIconCustomizer(themeName: String): Map<String, String>
  fun createEditorThemeCustomizer(themeName: String): Map<String, String>
}

@ApiStatus.Internal
open class UIThemeCustomizerImpl : UIThemeCustomizer {
  override fun createColorCustomizer(themeName: String): Map<String, Color> {
    return emptyMap()
  }

  override fun createNamedColorCustomizer(themeName: String): Map<String, String> {
    return emptyMap()
  }

  override fun createIconCustomizer(themeName: String): Map<String, String> {
    return emptyMap()
  }

  override fun createEditorThemeCustomizer(themeName: String): Map<String, String> {
    return emptyMap()
  }
}