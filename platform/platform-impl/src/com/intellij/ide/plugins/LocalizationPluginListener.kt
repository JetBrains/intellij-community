// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.LocalizationPluginHelper.isActiveLocalizationPlugin
import com.intellij.openapi.diagnostic.logger

internal class LocalizationPluginListener : DynamicPluginListener {

  override fun checkUnloadPlugin(pluginDescriptor: IdeaPluginDescriptor) {
    if (isActiveLocalizationPlugin(pluginDescriptor)) {
      logger<LocalizationPluginListener>().debug("throw CannotUnloadPluginException during unload Localization plugin")
      throw CannotUnloadPluginException("Localization plugin ${pluginDescriptor.pluginId} cannot be dynamically unloaded because it's selected language")
    }
  }
}