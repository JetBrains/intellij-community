package com.intellij.ide.customize.transferSettings.providers.vsmac.mappings

import com.intellij.ide.customize.transferSettings.db.KnownColorSchemes
import com.intellij.ide.customize.transferSettings.models.BundledEditorColorScheme
import java.util.*


object SchemesMappings {
  fun schemeMap(scheme: String) = when (scheme) {
    "Light" -> KnownColorSchemes.Light
    "Dark" -> KnownColorSchemes.Darcula
    "Gruvbox" -> KnownColorSchemes.Darcula
    "High Contrast Dark" -> KnownColorSchemes.HighContrast
    "High Contrast Light" -> KnownColorSchemes.Light
    "Monokai" -> KnownColorSchemes.Darcula
    "Nightshade" -> KnownColorSchemes.HighContrast
    "Oblivion" -> KnownColorSchemes.Darcula
    "Solarized Dark" -> KnownColorSchemes.Darcula
    "Solarized Light" -> KnownColorSchemes.Light
    "Tango" -> KnownColorSchemes.Light
    "Legacy – Dark" -> KnownColorSchemes.Darcula
    "Legacy – Light" -> KnownColorSchemes.Light
    else -> if (scheme.lowercase().endsWith("light")) KnownColorSchemes.Light else KnownColorSchemes.Darcula
  }
}