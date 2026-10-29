// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontCache
import com.intellij.openapi.editor.colors.EditorFontType
import org.jetbrains.annotations.ApiStatus
import java.awt.Font

/**
 * The application service behind [EditorFontCache.getInstance].
 * It holds the fonts of the global scheme.
 */
@ApiStatus.Internal
class GlobalSchemeFontCache : EditorFontCache() {
  private val cache = EditorFontCacheImpl { EditorColorsManager.getInstance().globalScheme }

  override fun getFont(key: EditorFontType?): Font {
    return cache.getFont(key)
  }

  override fun reset() {
    cache.reset()
  }
}
