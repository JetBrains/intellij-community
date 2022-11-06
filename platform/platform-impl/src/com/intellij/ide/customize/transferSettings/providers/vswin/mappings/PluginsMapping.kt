package com.intellij.ide.customize.transferSettings.providers.vswin.mappings

import com.intellij.ide.customize.transferSettings.db.KnownPlugins
import com.intellij.ide.customize.transferSettings.models.FeatureInfo

object PluginsMapping {
  fun get(id: String): FeatureInfo? {
    return when (id) {
      // CompanyName | ProductName
      //"Xavalon|XAML Styler" -> KnownPlugins.XAMLStyler
      "JetBrains s.r.o.|ReSharper" -> KnownPlugins.ReSharper
      // azure
      // prettier
      else -> processCustomCases(id)
    }
  }

  private fun processCustomCases(id: String): FeatureInfo? {
    return when {
      else -> null
    }
  }
}