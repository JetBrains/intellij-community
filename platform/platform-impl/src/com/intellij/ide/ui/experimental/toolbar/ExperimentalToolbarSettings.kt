// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.experimental.toolbar

import com.intellij.application.options.RegistryManager
import com.intellij.ide.ui.ExperimentalToolbarSettingsState
import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@State(
  name = "ToolbarSettingsService",
  storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))],
)
internal class ExperimentalToolbarSettings private constructor() : ToolbarSettings,
                                                                   Disposable {
  companion object {

    private val logger = logger<ExperimentalToolbarSettings>()

    private val NewToolbarEnabled = RegistryManager.getInstance().get("ide.new.navbar")
  }

  private var toolbarState = ExperimentalToolbarSettingsState()

  private inner class ToolbarRegistryListener : RegistryValueListener {

    override fun afterValueChanged(value: RegistryValue) {
      val booleanValue = value.asBoolean()
      toolbarState.showNewMainToolbar = booleanValue
      logger.info("New registry value: $booleanValue")

      val uiSettings = UISettings.instance
      val uiSettingsState = uiSettings.state
      uiSettingsState.showMainToolbar = !booleanValue && uiSettingsState.showMainToolbar
      uiSettingsState.showNavigationBar = !booleanValue && uiSettingsState.showNavigationBar
      uiSettings.fireUISettingsChanged()
    }
  }

  init {
    val application = ApplicationManager.getApplication()
    if (application == null || application.isDisposed) {
      throw ExtensionNotApplicableException.INSTANCE
    }

    Disposer.register(application, this)
    NewToolbarEnabled.addListener(ToolbarRegistryListener(), this)
  }

  override fun getState(): ExperimentalToolbarSettingsState = toolbarState

  override fun loadState(state: ExperimentalToolbarSettingsState) {
    toolbarState = state

    if (isEnabled) {
      logger.info("Loaded state: $state")
    }
  }

  override fun dispose() {}

  override var isEnabled: Boolean
    get() = NewToolbarEnabled.asBoolean()
    set(value) = NewToolbarEnabled.setValue(value)

  override var isVisible: Boolean
    get() = toolbarState.showNewMainToolbar
    set(value) {
      toolbarState.showNewMainToolbar = value

      val uiSettingState = UISettings.instance.state
      uiSettingState.showMainToolbar = !value && uiSettingState.showMainToolbar
    }
}
