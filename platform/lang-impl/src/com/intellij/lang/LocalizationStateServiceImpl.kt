// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.GeneralSettings
import com.intellij.ide.plugins.LocalizationPluginHelper
import com.intellij.ide.plugins.PluginManager
import com.intellij.l10n.LocalizationListener
import com.intellij.l10n.LocalizationStateService
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ReportValue
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import org.jetbrains.annotations.ApiStatus.Internal

private const val DEFAULT_LOCALE = "en"

@Internal
@State(
  name = "LocalizationStateService",
  category = SettingsCategory.SYSTEM,
  storages = [Storage(value = GeneralSettings.IDE_GENERAL_XML, usePathMacroManager = false)]
)
internal class LocalizationStateServiceImpl : LocalizationStateService, PersistentStateComponent<LocalizationState> {
  private var localizationState = LocalizationState()
  private var restartRequired: Boolean = false

  override fun initializeComponent() {
    val localizationProperty = EarlyAccessRegistryManager.getString(LocalizationUtil.LOCALIZATION_KEY)
    thisLogger().info("[i18n] Localization property from registry is $localizationProperty")
    if (!localizationProperty.isNullOrEmpty()) {
      EarlyAccessRegistryManager.setString(LocalizationUtil.LOCALIZATION_KEY, "")
      localizationState.selectedLocale = localizationProperty
      thisLogger().info("[i18n] Language defined from registry: $localizationProperty")
    }
  }

  override fun getState(): LocalizationState = localizationState

  override fun loadState(state: LocalizationState) {
    this.localizationState = state
  }

  override fun getSelectedLocale(): String = localizationState.selectedLocale

  override fun getLastSelectedLocale(): String = localizationState.lastSelectedLocale

  override fun isRestartRequired(): Boolean = restartRequired

  override fun setSelectedLocale(locale: String) {
    setSelectedLocale(locale, false)
  }

  override fun setSelectedLocale(locale: String, ignoreRestart: Boolean) {
    if (!restartRequired) {
      localizationState.lastSelectedLocale = localizationState.selectedLocale
    }
    localizationState.selectedLocale = locale
    restartRequired = if (ignoreRestart) false else selectedLocale != lastSelectedLocale
    ApplicationManager.getApplication().messageBus.syncPublisher(LocalizationListener.UPDATE_TOPIC).localeChanged()
  }

  override fun resetLocaleIfNeeded() {
    if (
      selectedLocale != DEFAULT_LOCALE &&
      LoadingState.COMPONENTS_LOADED.isOccurred &&
      PluginManager.getLoadedPlugins().none { LocalizationPluginHelper.isActiveLocalizationPlugin(it, selectedLocale) }
    ) {
      thisLogger().info("[i18n] Language setting was reset to default value: $DEFAULT_LOCALE; Previous value: ${selectedLocale}")
      localizationState.selectedLocale = DEFAULT_LOCALE
    }
  }
}

internal data class LocalizationState(
  @get:ReportValue(possibleValues = ["am", "ar", "as", "az", "bn", "cs", "da", "de", "el", "en", "es", "fa", "fr", "gu",
    "ha", "hi", "hu", "ig", "in", "it", "ja", "kk", "kn", "ko", "ml", "mr", "my", "nb",
    "ne", "nl", "nn", "no", "or", "pa", "pl", "pt", "ro", "ru", "rw", "sd", "si", "so",
    "sv", "ta", "te", "th", "tr", "uk", "ur", "uz", "vi", "yo", "zh", "zh-CN", "zu", "other"])
  var selectedLocale: String = DEFAULT_LOCALE,
  @get:ReportValue(possibleValues = ["am", "ar", "as", "az", "bn", "cs", "da", "de", "el", "en", "es", "fa", "fr", "gu",
    "ha", "hi", "hu", "ig", "in", "it", "ja", "kk", "kn", "ko", "ml", "mr", "my", "nb",
    "ne", "nl", "nn", "no", "or", "pa", "pl", "pt", "ro", "ru", "rw", "sd", "si", "so",
    "sv", "ta", "te", "th", "tr", "uk", "ur", "uz", "vi", "yo", "zh", "zh-CN", "zu", "other"])
  var lastSelectedLocale: String = DEFAULT_LOCALE,
)
