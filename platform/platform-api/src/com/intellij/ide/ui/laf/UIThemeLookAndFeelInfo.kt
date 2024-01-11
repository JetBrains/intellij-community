// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.UIDefaults

@Experimental
interface UIThemeLookAndFeelInfo  {
  val id: String

  @get:NlsSafe
  val name: String

  val isDark: Boolean

  val editorSchemeId: String?

  val isInitialized: Boolean

  val providerClassLoader: ClassLoader

  fun installTheme(defaults: UIDefaults)

  fun installEditorScheme(previousSchemeForLaf: EditorColorsScheme?)

  fun dispose()

  @Experimental
  @Internal
  fun describe(): UIThemeExportableBean
}

@Suppress("unused")
@Internal
@Experimental
class UIThemeExportableBean(
  /**
   * The map of color names to their corresponding
   * [32-bit SRGB color integer](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/Color) values.
   */
  val colors: Map<String, Int>,
  val iconColorsOnSelection: Map<String, Int>,
  val colorPalette: Map<String, String>,

  val icons: Map<String, String>,
)

@Internal
fun EditorColorsScheme.isDefaultForTheme(theme: UIThemeLookAndFeelInfo?): Boolean =
  (theme?.editorSchemeId ?: defaultNonLaFSchemeName()) == Scheme.getBaseName(name)

private fun defaultNonLaFSchemeName() = if (StartupUiUtil.isDarkTheme) "Darcula" else "Default"
