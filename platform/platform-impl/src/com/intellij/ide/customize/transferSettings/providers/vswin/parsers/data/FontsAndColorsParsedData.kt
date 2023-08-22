package com.intellij.ide.customize.transferSettings.providers.vswin.parsers.data

import com.intellij.ide.customize.transferSettings.providers.vswin.mappings.FontsAndColorsMappings

class FontsAndColorsParsedData(themeUuid: String) : VSParsedData {
  companion object {
    const val key: String = "Environment_FontsAndColors"
  }

  val theme: FontsAndColorsMappings.VsTheme = FontsAndColorsMappings.VsTheme.fromString(themeUuid)
}