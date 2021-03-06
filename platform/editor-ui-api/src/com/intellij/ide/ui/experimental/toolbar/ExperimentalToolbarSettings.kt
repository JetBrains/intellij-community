// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.experimental.toolbar

import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener

@State(name = "ToolbarSettingsService", storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))])
class ExperimentalToolbarSettings private constructor() : ToolbarSettings,
                                                          PersistentStateComponent<ExperimentalToolbarStateWrapper> {
  val logger = Logger.getInstance(ExperimentalToolbarSettings::class.java)
  val newToolbarEnabled: Boolean
    get() = Registry.`is`("ide.new.navbar", false)

  private var toolbarState = ExperimentalToolbarStateWrapper()

  private val disposable = Disposer.newDisposable()

  inner class ToolbarRegistryListener : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      val v = value.asBoolean()
      toolbarState.state =
        getToolbarStateByVisibilityFlags(v, if(v) false else isToolbarVisible(), v,
                                         if(v) false else isNavBarVisible())
      logger.info("Registry value new.navbar was changed to $v, toolbar state is " + toolbarState.state)
      updateSettingsState()
      UISettings.instance.fireUISettingsChanged()
    }
  }

  init {
    if (!newToolbarEnabled) {
      toolbarState.state = getToolbarStateByVisibilityFlags(false, UISettings.instance.state.showMainToolbar, false,
                                                            UISettings.instance.state.showNavigationBar)
    }
    Disposer.register(ApplicationManager.getApplication(), disposable)
    Registry.get("ide.new.navbar").addListener(ToolbarRegistryListener(), disposable)
  }

  override fun getState(): ExperimentalToolbarStateWrapper {
    return toolbarState
  }

  override fun loadState(state: ExperimentalToolbarStateWrapper) {
    if (!newToolbarEnabled) {
      val oldState = UISettings.instance.state
      toolbarState.state =
        getToolbarStateByVisibilityFlags(false, oldState.showMainToolbar, false,
                                         oldState.showNavigationBar)
      logger.info("Loading old state, main toolbar: ${oldState.showMainToolbar} navBar ${oldState.showNavigationBar}")

    }
    else {
      toolbarState = state
      updateSettingsState()
    }
  }

  fun getToolbarStateByVisibilityFlags(newToolbarEnabled: Boolean, oldToolbarVisible: Boolean,
                                       newToolbarVisible: Boolean, navBarVisible: Boolean): ExperimentalToolbarStateEnum {
    if (oldToolbarVisible && newToolbarVisible) {
      logger.error("Illegal double toolbar visible state")
      throw IllegalStateException()
    }
    if (newToolbarEnabled && newToolbarVisible) {
      if (navBarVisible) {
        return ExperimentalToolbarStateEnum.NEW_TOOLBAR_WITH_NAVBAR
      }
      else {
        return ExperimentalToolbarStateEnum.NEW_TOOLBAR_WITHOUT_NAVBAR
      }
    }
    else if (oldToolbarVisible) {
      if (navBarVisible) {
        return ExperimentalToolbarStateEnum.OLD_TOOLBAR_WITH_NAVBAR_SEPARATE
      }
      else {
        return ExperimentalToolbarStateEnum.OLD_TOOLBAR_WITHOUT_NAVBAR
      }
    }
    else {
      if (navBarVisible) {
        return ExperimentalToolbarStateEnum.OLD_NAVBAR
      }
      else {
        return ExperimentalToolbarStateEnum.NO_TOOLBAR_NO_NAVBAR
      }
    }
  }

  override fun isNavBarVisible(): Boolean {
    return toolbarState.state.navBarVisible
  }

  override fun setNavBarVisible(value: Boolean) {

    toolbarState.state = getToolbarStateByVisibilityFlags(newToolbarEnabled, toolbarState.state.oldToolbarVisible,
                                                          toolbarState.state.newToolbarVisible,
                                                          value)
    updateSettingsState()
    UISettings.instance.fireUISettingsChanged()
  }

  private fun updateSettingsState() {
    UISettings.instance.state.showNavigationBar = toolbarState.state.navBarVisible
    UISettings.instance.state.showMainToolbar = toolbarState.state.oldToolbarVisible
    logger.info("showNavigationBar: ${UISettings.instance.state.showNavigationBar} showMainToolbar: ${UISettings.instance.state.showMainToolbar}")
  }

  override fun isToolbarVisible(): Boolean {
    return toolbarState.state.oldToolbarVisible
  }

  override fun setToolbarVisible(value: Boolean) {
    if (value) {
      toolbarState.state = getToolbarStateByVisibilityFlags(newToolbarEnabled, value, false, toolbarState.state.navBarVisible)
    }
    else {
      toolbarState.state = getToolbarStateByVisibilityFlags(newToolbarEnabled, value, toolbarState.state.newToolbarVisible,
                                                            toolbarState.state.navBarVisible)
    }
    updateSettingsState()
    UISettings.instance.fireUISettingsChanged()
  }

  override fun getShowToolbarInNavigationBar(): Boolean {
    return toolbarState.state == ExperimentalToolbarStateEnum.OLD_NAVBAR
  }

  var showNewToolbar: Boolean
    get() = toolbarState.state.newToolbarVisible
    set(value) {
      if (value) {
        toolbarState.state = getToolbarStateByVisibilityFlags(newToolbarEnabled, false, value, toolbarState.state.navBarVisible)
      }
      else {
        toolbarState.state = getToolbarStateByVisibilityFlags(newToolbarEnabled, toolbarState.state.oldToolbarVisible, value,
                                                              toolbarState.state.navBarVisible)
      }
      updateSettingsState()
      UISettings.instance.fireUISettingsChanged()
    }
}



