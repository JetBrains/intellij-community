// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.DevTimeClassLoader
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Temporary utility class for migration to the new UI.
 * This is not a public API. For plugin development use [NewUI.isEnabled]
 *
 * @author Konstantin Bulenkov
 */
@Internal
abstract class ExperimentalUI {
  companion object {
    @Suppress("DEPRECATION")
    const val KEY: String = NewUiValue.KEY

    // Means that IDE is started after enabling the New UI (not necessary the first time).
    // Should be unset by the client, or it will be unset on the IDE close.
    const val NEW_UI_SWITCH: String = "experimental.ui.switch"
    var forcedSwitchedUi: Boolean = false

    const val SWITCHED_FROM_CLASSIC_TO_ISLANDS: String = "switched.from.classic.to.islands"
    val switchedFromClassicToIslands: Boolean?
      get() = EarlyAccessRegistryManager.getString(SWITCHED_FROM_CLASSIC_TO_ISLANDS)?.toBoolean()

    @Volatile
    var cleanUpClassicUIFromDisabled: Runnable? = null

    var SHOW_NEW_UI_ONBOARDING_ON_START: Boolean
      get() = PropertiesComponent.getInstance().getBoolean(SHOW_NEW_UI_ONBOARDING_ON_START_KEY)
      set(value) = PropertiesComponent.getInstance().setValue(SHOW_NEW_UI_ONBOARDING_ON_START_KEY, value)

    var wasThemeReset: Boolean = false

    private const val SHOW_NEW_UI_ONBOARDING_ON_START_KEY = "show.new.ui.onboarding.on.start"

    @Internal
    @JvmField
    val EP_LISTENER: ExtensionPointName<Listener> = ExtensionPointName("com.intellij.uiChangeListener")

    init {
      NewUiValue.initialize {
        if (ApplicationManager.getApplication() == null) {
          EarlyAccessRegistryManager.getBoolean(KEY)
        }
        else {
          getInstance().earlyInitValue()
        }
      }
    }

    @JvmStatic
    fun getInstance(): ExperimentalUI = ApplicationManager.getApplication().service<ExperimentalUI>()

    @JvmStatic
    fun isNewUI(): Boolean {
      // always true for development time tools, e.g., in Compose UI Preview
      if (Thread.currentThread().contextClassLoader is DevTimeClassLoader) return true

      return NewUiValue.isEnabled()
    }

    val isNewNavbar: Boolean
      get() = NewUiValue.isEnabled() && Registry.`is`("ide.experimental.ui.navbar.scroll", true)

    val isEditorTabsWithScrollBar: Boolean
      get() = NewUiValue.isEnabled() && Registry.`is`("ide.experimental.ui.editor.tabs.scrollbar", true)

  }

  open fun setNewUIInternal(newUI: Boolean, suggestRestart: Boolean) {
    // TODO: remove all callers
  }

  // used by the JBClient for cases where a link overrides new UI mode
  abstract fun saveCurrentValueAndReapplyDefaultLaf()

  open fun lookAndFeelChanged() {
  }

  open suspend fun installIconPatcher() {
  }

  open fun earlyInitValue(): Boolean = EarlyAccessRegistryManager.getBoolean(KEY)

  /**
   * Interface to mark renderers compliant with the new UI design guidelines.
   */
  @Internal
  interface NewUIComboBoxRenderer

  @Internal
  interface Listener {
    fun changeUI(value: Boolean)
  }
}