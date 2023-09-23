// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import javax.swing.UIDefaults

@ApiStatus.Internal
@ApiStatus.Experimental
interface UIThemeLookAndFeelInfo  {
  val id: String
  @get:NlsSafe
  val name: String
  val isDark: Boolean

  val editorSchemeName: String?
  val isInitialized: Boolean

  val providerClassLoader: ClassLoader

  fun installTheme(defaults: UIDefaults)

  fun installEditorScheme(previousSchemeForLaf: EditorColorsScheme?)

  fun dispose()
}
