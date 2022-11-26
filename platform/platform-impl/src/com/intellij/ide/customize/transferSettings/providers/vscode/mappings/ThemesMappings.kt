package com.intellij.ide.customize.transferSettings.providers.vscode.mappings

import com.intellij.ide.customize.transferSettings.db.KnownLafs

object ThemesMappings {
  fun themeMap(theme: String) = when (theme) {
    "vs" -> KnownLafs.Light
    "vs-dark" -> KnownLafs.Darcula
    "hc-black" -> KnownLafs.HighContrast
    "Monokai" -> KnownLafs.Darcula //KnownLafs.MonokaiSpectrum
    "Solarized Dark" -> KnownLafs.Darcula //KnownLafs.SolarizedDark
    "Solarized Light" -> KnownLafs.Darcula //KnownLafs.SolarizedLight
    else -> otherThemeConverter(theme)
  }

  private fun otherThemeConverter(theme: String) = if (theme.lowercase().contains("light")) KnownLafs.Light else KnownLafs.Darcula
}