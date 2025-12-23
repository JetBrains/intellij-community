// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.openapi.application.ApplicationManager

internal class GutterIconsSearchableOptionContributor : SearchableOptionContributor() {
  override fun processOptions(processor: SearchableOptionProcessor) {
    val gutterIconsDisplayName = IdeBundle.message("configurable.GutterIconsConfigurable.display.name")
    val app = ApplicationManager.getApplication()
    LineMarkerProviders.EP_NAME.processWithPluginDescriptor { extension, pluginDescriptor ->
      val instance = extension.getInstance(app, pluginDescriptor) as? LineMarkerProviderDescriptor ?: return@processWithPluginDescriptor
      val name = instance.getName()
      if (!name.isNullOrEmpty()) {
        processor.addOptions(name, null, name, GutterIconsConfigurable.ID, gutterIconsDisplayName, true)
      }
    }
  }
}
