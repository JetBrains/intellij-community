// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.ide.ui.LafManager
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.impl.patchers.DefaultSvgPatcher
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.ui.icons.pathTransform
import com.intellij.ui.icons.pathTransformGlobalModCount
import com.intellij.util.SVGLoader

/**
 * This is currently implemented in a naive way that just forces reloads based on LaF ID.
 * In the future, this might be adjusted to allow custom icon theming manipulations.
 */
internal class IntelliJThemeContext: ThemeContext {
  override fun digest(): String {
    val lafId = LafManager.getInstance().currentUIThemeLookAndFeel?.id ?: "none"
    return "$lafId;$pathTransformGlobalModCount"
  }
  override fun imageModifiers(): ImageModifiers {
    return IntelliJImageModifiers(
      null,
      null,
      pathTransform.get().isDark,
      null,
      SVGLoader.colorPatcherProvider
    )
  }
}

class IntelliJImageModifiers(
  colorFilter: ColorFilter? = null,
  svgPatcher: DefaultSvgPatcher? = null,
  isDark: Boolean = false,
  stroke: Color? = null,
  val legacyPatcherProvider: SVGLoader.SvgElementColorPatcherProvider? = null
): DefaultImageModifiers(colorFilter, svgPatcher, isDark, stroke)