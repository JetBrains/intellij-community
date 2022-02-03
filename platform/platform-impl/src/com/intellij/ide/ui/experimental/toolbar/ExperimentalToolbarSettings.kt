// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.experimental.toolbar

import com.intellij.application.options.RegistryManager
import com.intellij.ide.ui.ExperimentalToolbarSettingsState
import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import org.jetbrains.annotations.ApiStatus

private const val REGISTRY_KEY = "ide.widget.toolbar"
private val logger = logger<ExperimentalToolbarSettings>()

@ApiStatus.Experimental
@State(name = "ToolbarSettingsService", storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))])
internal class ExperimentalToolbarSettings private constructor() : ToolbarSettings, UISettingsListener, Disposable {
  private var toolbarState = ExperimentalToolbarSettingsState()

  init {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(UISettingsListener.TOPIC, this)
  }

  @Suppress("unused")
  private class ToolbarRegistryListener : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      if (value.key != REGISTRY_KEY) {
        return
      }

      val booleanValue = value.asBoolean()
      logger.info("New registry value: $booleanValue")

      ToolbarSettings.getInstance().isVisible = booleanValue

      val uiSettings = UISettings.instance
      uiSettings.showNavigationBar = !booleanValue && uiSettings.showNavigationBar
      uiSettings.fireUISettingsChanged()
    }
  }

  override fun getState(): ExperimentalToolbarSettingsState = toolbarState

  override fun loadState(state: ExperimentalToolbarSettingsState) {
    toolbarState = state
  }

  override fun dispose() {}

  override var isEnabled: Boolean
    get() = RegistryManager.getInstance().`is`(REGISTRY_KEY)
    set(value) = RegistryManager.getInstance().get(REGISTRY_KEY).setValue(value)

  override var isVisible: Boolean
    get() = toolbarState.showNewMainToolbar
    set(value) {
      toolbarState.showNewMainToolbar = value
      hideClassicMainToolbarAndNavbarIfVisible()
    }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    logger.info("Dispatching UI settings change.")
    hideClassicMainToolbarAndNavbarIfVisible()
  }

  private fun hideClassicMainToolbarAndNavbarIfVisible() {
    if (isVisible) {
      logger.info("Hiding Main Toolbar (Classic) because the new toolbar is visible.")
      UISettings.instance.showMainToolbar = false
      logger.info("Hiding NavBar because the new toolbar is visible.")
      UISettings.instance.showNavigationBar = false
    }
  }
}
