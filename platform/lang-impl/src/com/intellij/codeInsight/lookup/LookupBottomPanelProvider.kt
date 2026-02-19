// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Allows providing a custom panel showing in the bottom of the lookup.
 * If there are several providers that return a non-null value from [createBottomPanel], the first one will be used.
 * The order of providers can be specified using `order` attribute in plugin.xml.
 * If all providers return `null`, the default panel will be used.
 */
@ApiStatus.Experimental
interface LookupBottomPanelProvider {
  fun createBottomPanel(lookup: Lookup): JComponent?

  companion object {
    private val EP_NAME = ExtensionPointName<LookupBottomPanelProvider>("com.intellij.lookup.bottomPanelProvider")

    @ApiStatus.Internal
    @JvmStatic
    fun createPanel(lookup: Lookup): JComponent? {
      for (extension in EP_NAME.extensionList) {
        val panel = extension.createBottomPanel(lookup)
        if (panel != null) {
          return panel
        }
      }
      return null
    }
  }
}