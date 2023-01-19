package com.intellij.ide.customize.transferSettings.providers.vswin.parsers.data

import com.intellij.ide.customize.transferSettings.providers.vswin.mappings.FontsAndColorsMappings

class FontsAndColorsParsedData(themeUuid: String) : VSParsedData {
  companion object {
    const val key = "Environment_FontsAndColors"
  }

  val theme = FontsAndColorsMappings.VsTheme.fromString(themeUuid)
}