// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.annotations.SystemDependent

@Service(Service.Level.APP)
@State(name = "GeneralLocalSettings",
       storages = [Storage(value = "ide.general.local.xml", roamingType = RoamingType.DISABLED)])
class GeneralLocalSettings : SimplePersistentStateComponent<GeneralLocalState>(GeneralLocalState()) {
  companion object {
    private const val MIGRATED_FROM_GENERAL_SETTINGS = "migrated.non.roamable.values.from.general.settings"

    @JvmStatic
    fun getInstance(): GeneralLocalSettings {
      return ApplicationManager.getApplication().service<GeneralLocalSettings>()
    }

    private fun getDefaultAlternativeBrowserPath(): String {
      return when {
        SystemInfoRt.isWindows -> "C:\\Program Files\\Internet Explorer\\IExplore.exe"
        SystemInfoRt.isMac -> "open"
        SystemInfoRt.isUnix -> "/usr/bin/firefox"
        else -> ""
      }
    }
  }

  override fun noStateLoaded() {
    migrateFromGeneralSettings()
  }

  private fun migrateFromGeneralSettings() {
    val propertyManager = PropertiesComponent.getInstance()
    if (propertyManager.getBoolean(MIGRATED_FROM_GENERAL_SETTINGS, false)) {
      return
    }

    propertyManager.setValue(MIGRATED_FROM_GENERAL_SETTINGS, true)

    val generalSettingsState = GeneralSettings.getInstance().state
    defaultProjectDirectory = generalSettingsState.defaultProjectDirectory ?: ""
    useDefaultBrowser = generalSettingsState.useDefaultBrowser
    browserPath = generalSettingsState.browserPath ?: ""

    generalSettingsState.defaultProjectDirectory = ""
    generalSettingsState.useDefaultBrowser = true
    generalSettingsState.browserPath = ""
  }

  var defaultProjectDirectory: @SystemDependent String
    get() = state.defaultProjectDirectory ?: ""
    set(value) {
      state.defaultProjectDirectory = value
    }

  var browserPath: String
    get() = state.browserPath ?: getDefaultAlternativeBrowserPath()
    set(value) {
      state.browserPath = value
    }

  var useDefaultBrowser: Boolean
    get()  = state.useDefaultBrowser
    set(value) {
      state.useDefaultBrowser = value
    }
}

class GeneralLocalState : BaseState() {
  var defaultProjectDirectory: String? by string("")
  var useDefaultBrowser: Boolean by property(true)
  var browserPath: String? by string(null)
}