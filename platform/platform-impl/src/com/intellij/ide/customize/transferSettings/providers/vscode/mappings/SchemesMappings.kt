package com.intellij.ide.customize.transferSettings.providers.vscode.mappings

import com.intellij.ide.customize.transferSettings.db.KnownColorSchemes

object SchemesMappings {
  fun schemeMap(scheme: String) = when (scheme) {
    "vs" -> KnownColorSchemes.Light
    "vs-dark" -> KnownColorSchemes.Darcula
    "hc-black" -> KnownColorSchemes.HighContrast
    "Monokai" -> KnownColorSchemes.Darcula //KnownColorSchemes.Monokai
    "Solarized Dark" -> KnownColorSchemes.Darcula //KnownColorSchemes.SolarizedDark
    "Solarized Light" -> KnownColorSchemes.Light //KnownColorSchemes.SolarizedLight
    else -> otherSchemeConverter(scheme)
  }

  private fun otherSchemeConverter(scheme: String) = if (scheme.lowercase().contains("light")) KnownColorSchemes.Light
  else KnownColorSchemes.Darcula
}