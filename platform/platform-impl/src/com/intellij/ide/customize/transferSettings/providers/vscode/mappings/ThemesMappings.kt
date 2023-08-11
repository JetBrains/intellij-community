package com.intellij.ide.customize.transferSettings.providers.vscode.mappings

import com.intellij.ide.customize.transferSettings.db.KnownLafs
import com.intellij.ide.customize.transferSettings.models.BundledLookAndFeel

object ThemesMappings {
  fun themeMap(theme: String): BundledLookAndFeel = when (theme) {
    "vs" -> KnownLafs.Light
    "vs-dark" -> KnownLafs.Darcula
    "hc-black" -> KnownLafs.HighContrast
    "Monokai" -> KnownLafs.Darcula //KnownLafs.MonokaiSpectrum
    "Solarized Dark" -> KnownLafs.Darcula //KnownLafs.SolarizedDark
    "Solarized Light" -> KnownLafs.Light //KnownLafs.SolarizedLight
    else -> otherThemeConverter(theme)
  }

  private fun otherThemeConverter(theme: String) = if (theme.lowercase().contains("light")) KnownLafs.Light else KnownLafs.Darcula
}