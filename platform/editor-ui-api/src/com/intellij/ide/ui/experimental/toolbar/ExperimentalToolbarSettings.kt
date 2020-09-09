// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.experimental.toolbar

import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.*
import com.intellij.openapi.util.registry.Registry

@State(name = "ToolbarSettingsService", storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))])
class ExperimentalToolbarSettings private constructor() : ToolbarSettings,
                                                          PersistentStateComponent<ExperimentalToolbarStateWrapper> {

  val newToolbarEnabled: Boolean
    get() = Registry.`is`("ide.new.navbar", false)

  val showNewNavbarVcsGroup: Boolean
    get() = Registry.`is`("ide.new.navbar.vcs.group", false)

  val showNewNavbarRunGroup: Boolean
    get() = Registry.`is`("ide.new.navbar.run.debug", false)

  private var toolbarState = ExperimentalToolbarStateWrapper()


  override fun getState(): ExperimentalToolbarStateWrapper {
    return toolbarState
  }

  override fun loadState(state: ExperimentalToolbarStateWrapper) {
    toolbarState = state
    updateSettingsState()
  }

  fun getToolbarStateByVisibilityFlags(newToolbarEnabled: Boolean, oldToolbarVisible: Boolean,
                                       newToolbarVisible: Boolean, navBarVisible: Boolean): ExperimentalToolbarStateEnum {
    if (oldToolbarVisible && newToolbarVisible) {
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
    return toolbarState.equals(ExperimentalToolbarStateEnum.OLD_NAVBAR)
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



