package com.intellij.ide.customize.transferSettings.providers.vsmac.mappings

import com.intellij.ide.customize.transferSettings.db.KnownLafs

object ThemesMappings {
  fun themeMap(theme: String) = when (theme) {
    "Dark" -> KnownLafs.Darcula
    "Light" -> KnownLafs.Light
    else -> null
  }
}