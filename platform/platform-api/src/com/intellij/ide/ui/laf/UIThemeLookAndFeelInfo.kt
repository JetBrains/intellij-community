// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.UIDefaults

/**
 * Interface representing a UI theme look and feel.
 */
@ApiStatus.NonExtendable
interface UIThemeLookAndFeelInfo  {
  /**
   * Returns the unique identifier of the theme.
   */
  val id: String

  /**
   * Returns the display name of the theme.
   * If the name is not specified, the ID is used instead.
   */
  @get:NlsSafe
  val name: String

  /**
   * Returns the author of the theme, or null if not specified.
   */
  val author: String?

  /**
   * Returns whether this theme is a dark theme.
   */
  val isDark: Boolean

  /**
   * Returns the ID of the editor color scheme associated with this theme,
   * or null if no specific scheme is associated.
   */
  val editorSchemeId: String?

  /**
   * Returns whether this theme has been initialized and installed.
   */
  val isInitialized: Boolean

  /**
   * Returns the ClassLoader that provides resources for this theme.
   */
  val providerClassLoader: ClassLoader

  /**
   * Installs this theme by applying theme-specific settings to the provided UIDefaults.
   * This includes setting up icon patchers, SVG color patchers, and background images.
   *
   * @param defaults the UIDefaults to apply theme settings to
   */
  fun installTheme(defaults: UIDefaults)

  /**
   * Installs the editor color scheme associated with this theme.
   * If a previous scheme is provided, it will be used; otherwise, the scheme specified
   * by the theme's editorSchemeId will be used.
   *
   * @param previousSchemeForLaf the previous editor color scheme to use, or null to use the theme's default
   */
  fun installEditorScheme(previousSchemeForLaf: EditorColorsScheme?)

  /**
   * Disposes of resources used by this theme, including removing icon patchers
   * and restoring background properties to their previous values.
   */
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

val UIThemeLookAndFeelInfo.defaultSchemeName: String @Internal get() = editorSchemeId ?: defaultNonLaFSchemeName(isDark)
private fun defaultNonLaFSchemeName() = defaultNonLaFSchemeName(StartupUiUtil.isDarkTheme)
@Internal
fun defaultNonLaFSchemeName(dark: Boolean): String = if (dark) "Darcula" else EditorColorsScheme.getDefaultSchemeName()

val UIThemeLookAndFeelInfo.isThemeFromPlugin: Boolean @Internal get() {
  val pluginClassLoader = providerClassLoader as? PluginAwareClassLoader
  return pluginClassLoader?.pluginDescriptor?.isBundled == false
}
