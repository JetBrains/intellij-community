// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.util.gotoByName.FindActionSearchableOptionsFilter

internal class PluginSearchableOptionContributor : SearchableOptionContributor() {
  override fun processOptions(processor: SearchableOptionProcessor) {
    val configurableName = IdeBundle.message("title.plugins")
    PluginManager.getVisiblePlugins(false).forEach { descriptor ->
      val pluginName = descriptor.name
      processor.addOptions(pluginName, null, pluginName, PluginManagerConfigurable.ID, configurableName, false)
      descriptor.description?.let {
        processor.addOptions(it, null, pluginName, PluginManagerConfigurable.ID, configurableName, false)
      }
    }
  }
}

internal class PluginSearchableOptionsActionFilter : FindActionSearchableOptionsFilter {
  override fun isAvailable(description: OptionDescription): Boolean =
    description.configurableId != PluginManagerConfigurable.ID
}
