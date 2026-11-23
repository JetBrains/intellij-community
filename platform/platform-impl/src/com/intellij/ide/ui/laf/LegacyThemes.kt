// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.ide.ui.UITheme
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.ui.ExperimentalUI
import org.jetbrains.annotations.ApiStatus

/**
 * The legacy themes of the new UI are Dark, Light, Light with Light Header, and Darcula. The Islands themes replace them.
 *
 * The [SHOW_LEGACY_THEMES_KEY] registry key controls if the theme lists of the new UI show the legacy themes.
 * If the user uses a legacy theme, [LafManagerImpl] sets the key to `true`.
 */
@ApiStatus.Internal
object LegacyThemes {

  const val SHOW_LEGACY_THEMES_KEY: String = "ide.show.legacy.themes"

  private val themeIds = setOf(UITheme.EXPERIMENTAL_DARK_ID,
                               UITheme.EXPERIMENTAL_LIGHT_ID,
                               UITheme.EXPERIMENTAL_LIGHT_WITH_LIGHT_HEADER_ID,
                               "Darcula")

  val isShowLegacyThemes: Boolean
    get() = RegistryManager.getInstance().`is`(SHOW_LEGACY_THEMES_KEY)

  /**
   * Returns `true` if the theme lists hide [theme].
   */
  fun isHidden(theme: UIThemeLookAndFeelInfo): Boolean =
    theme.id in themeIds && ExperimentalUI.isNewUI() && !isShowLegacyThemes

  /**
   * Sets the [SHOW_LEGACY_THEMES_KEY] registry key to `true` if [themeIds] contain a legacy theme.
   */
  internal fun showIfUsed(themeIds: Collection<String>) {
    if (!ExperimentalUI.isNewUI() || isShowLegacyThemes) {
      return
    }

    if (themeIds.any { it in this.themeIds }) {
      RegistryManager.getInstance().get(SHOW_LEGACY_THEMES_KEY).setValue(true)
    }
  }
}
