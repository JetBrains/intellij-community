// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.models

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import org.jetbrains.annotations.Nls

interface EditorColorScheme {
  val displayName: @Nls String
}

class BundledEditorColorScheme(override val displayName: @Nls String, val scheme: EditorColorsScheme): EditorColorScheme {
  companion object {
    fun fromManager(name: String) = EditorColorsManager.getInstance().getScheme(name)
      ?.let { BundledEditorColorScheme(it.displayName, it) }
  }
}

class PluginEditorColorScheme(override val displayName: @Nls String, val pluginId: String, val installedName: String, val fallback: BundledEditorColorScheme) : EditorColorScheme