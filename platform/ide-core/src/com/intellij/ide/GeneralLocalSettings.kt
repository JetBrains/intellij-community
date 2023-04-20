// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.SystemDependent

@Service(Service.Level.APP)
@State(name = "GeneralLocalSettings",
       storages = [Storage(value = "ide.general.local.xml", roamingType = RoamingType.DISABLED)])
class GeneralLocalSettings : SimplePersistentStateComponent<GeneralLocalSettings.GeneralLocalState>(GeneralLocalState()) {

  class GeneralLocalState : BaseState() {
    var defaultProjectDirectory by string("")
    var useDefaultBrowser by property(true)
    var browserPath by string(null)
  }

  override fun noStateLoaded() {
    migrateFromGeneralSettings()
  }

  private fun migrateFromGeneralSettings() {
    if (!PropertiesComponent.getInstance().getBoolean(MIGRATED_FROM_GENERAL_SETTINGS, false)) {
      PropertiesComponent.getInstance().setValue(MIGRATED_FROM_GENERAL_SETTINGS, true)

      defaultProjectDirectory = GeneralSettings.getInstance().myDefaultProjectDirectory
      useDefaultBrowser = GeneralSettings.getInstance().myUseDefaultBrowser
      browserPath = GeneralSettings.getInstance().myBrowserPath

      GeneralSettings.getInstance().myDefaultProjectDirectory = ""
      GeneralSettings.getInstance().myUseDefaultBrowser = true
      GeneralSettings.getInstance().myBrowserPath = ""
    }
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


  companion object {
    private const val MIGRATED_FROM_GENERAL_SETTINGS = "migrated.non.roamable.values.from.general.settings"

    @JvmStatic
    fun getInstance(): GeneralLocalSettings {
      return ApplicationManager.getApplication().getService(GeneralLocalSettings::class.java)
    }

    private fun getDefaultAlternativeBrowserPath(): String {
      return if (SystemInfo.isWindows) {
        "C:\\Program Files\\Internet Explorer\\IExplore.exe"
      }
      else if (SystemInfo.isMac) {
        "open"
      }
      else if (SystemInfo.isUnix) {
        "/usr/bin/firefox"
      }
      else {
        ""
      }
    }
  }
}