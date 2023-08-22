package com.intellij.ide.customize.transferSettings.providers.vsmac.mappings

import com.intellij.ide.customize.transferSettings.db.KnownLafs
import com.intellij.ide.customize.transferSettings.models.BundledLookAndFeel

object ThemesMappings {
  fun themeMap(theme: String): BundledLookAndFeel? = when (theme) {
    "Dark" -> KnownLafs.Darcula
    "Light" -> KnownLafs.Light
    else -> null
  }
}