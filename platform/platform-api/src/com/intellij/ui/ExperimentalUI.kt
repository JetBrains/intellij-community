// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
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

    // last IDE version when the New UI was enabled
    const val NEW_UI_USED_VERSION: String = "experimental.ui.used.version"
    const val NEW_UI_FIRST_SWITCH: String = "experimental.ui.first.switch"

    // Means that IDE is started after enabling the New UI (not necessary the first time).
    // Should be unset by the client, or it will be unset on the IDE close.
    const val NEW_UI_SWITCH: String = "experimental.ui.switch"
    var forcedSwitchedUi: Boolean = false
    var wasThemeReset = false

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
    fun isNewUI(): Boolean = NewUiValue.isEnabled()

    val isNewNavbar: Boolean
      get() = NewUiValue.isEnabled() && Registry.`is`("ide.experimental.ui.navbar.scroll", true)

    val isEditorTabsWithScrollBar: Boolean
      get() = NewUiValue.isEnabled() && Registry.`is`("ide.experimental.ui.editor.tabs.scrollbar", true)

    val isNewUiUsedOnce: Boolean
      /** Whether New UI was enabled at least once. Note: tracked since 2023.1  */
      get() {
        val propertiesComponent = PropertiesComponent.getInstance()
        return propertiesComponent.getValue(NEW_UI_USED_VERSION) != null || propertiesComponent.getBoolean("experimental.ui.used.once")
      }
  }

  open fun setNewUIInternal(newUI: Boolean, suggestRestart: Boolean) {
    // TODO: remove all callers
  }

  // used by the JBClient for cases where a link overrides new UI mode
  abstract fun saveCurrentValueAndReapplyDefaultLaf()

  open fun lookAndFeelChanged() {
  }

  open fun installIconPatcher() {
  }

  open fun earlyInitValue() = EarlyAccessRegistryManager.getBoolean(KEY)

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