// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.LocalizationPluginHelper.isActiveLocalizationPlugin
import com.intellij.ide.ui.LanguageAndRegionUi
import com.intellij.l10n.LocalizationStateService
import com.intellij.openapi.diagnostic.logger
import java.util.Locale

internal class LocalizationPluginListener : DynamicPluginListener {

  override fun checkUnloadPlugin(pluginDescriptor: IdeaPluginDescriptor) {
    if (isActiveLocalizationPlugin(pluginDescriptor)) {
      logger<LocalizationPluginListener>().info("[i18n] Language setting was reset to default during unload Localization plugin")

      LocalizationStateService.getInstance()?.setSelectedLocale(Locale.ENGLISH.toLanguageTag())

      LanguageAndRegionUi.showRestartDialog(false)
    }
  }
}