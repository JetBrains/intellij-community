// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang

import com.intellij.ide.GeneralSettings
import com.intellij.ide.plugins.LocalizationPluginHelper
import com.intellij.ide.plugins.PluginManager
import com.intellij.l10n.LocalizationListener
import com.intellij.l10n.LocalizationStateService
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import org.jetbrains.annotations.ApiStatus.Internal

private const val DEFAULT_LOCALE = "en"

@Internal
@State(name = "LocalizationStateService", storages = [Storage(GeneralSettings.IDE_GENERAL_XML)])
internal class LocalizationStateServiceImpl : LocalizationStateService, PersistentStateComponent<LocalizationState> {

  private var localizationState = LocalizationState()

  override fun initializeComponent() {
    val localizationProperty = EarlyAccessRegistryManager.getString(LocalizationUtil.LOCALIZATION_KEY)
    if (!localizationProperty.isNullOrEmpty()) {
      setSelectedLocale(localizationProperty)
      EarlyAccessRegistryManager.setString(LocalizationUtil.LOCALIZATION_KEY, "")
    }
    if (PluginManager.getLoadedPlugins().none { LocalizationPluginHelper.isActiveLocalizationPlugin(it, getSelectedLocale()) }) {
      setSelectedLocale(DEFAULT_LOCALE)
    }
  }

  override fun getState(): LocalizationState {
    return localizationState
  }

  override fun loadState(state: LocalizationState) {
    this.localizationState = state
  }

  override fun getSelectedLocale(): String {
    return localizationState.selectedLocale
  }

  override fun setSelectedLocale(locale: String) {
    localizationState.lastSelectedLocale = localizationState.selectedLocale
    localizationState.selectedLocale = locale
    ApplicationManager.getApplication().messageBus.syncPublisher(LocalizationListener.Companion.UPDATE_TOPIC).localeChanged()
  }
}

@Internal
data class LocalizationState(
  var selectedLocale: String = DEFAULT_LOCALE,
  var lastSelectedLocale: String = DEFAULT_LOCALE
)

