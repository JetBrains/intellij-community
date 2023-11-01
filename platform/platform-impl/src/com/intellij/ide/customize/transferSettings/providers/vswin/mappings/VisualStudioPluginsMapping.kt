// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.providers.vswin.mappings

import com.intellij.ide.customize.transferSettings.db.KnownPlugins
import com.intellij.ide.customize.transferSettings.models.FeatureInfo

object VisualStudioPluginsMapping {

  const val ReSharper = "JetBrains s.r.o.|ReSharper"

  fun get(id: String): FeatureInfo? {
    return when (id) {
      // CompanyName | ProductName
      //"Xavalon|XAML Styler" -> KnownPlugins.XAMLStyler
      ReSharper -> KnownPlugins.ReSharper
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