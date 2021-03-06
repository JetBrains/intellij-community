// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

import com.intellij.openapi.components.*

class CoreToolbarSettings private constructor() : ToolbarSettings{

  private var uiStateSettings: UISettingsState? = null

  override fun isNavBarVisible(): Boolean {
    return uiSettingsState().showNavigationBar
  }

  override fun setNavBarVisible(value: Boolean) {
    uiSettingsState().showNavigationBar = value
  }

  override fun isToolbarVisible(): Boolean {
    return uiSettingsState().showMainToolbar
  }

  override fun setToolbarVisible(b: Boolean) {
    uiSettingsState().showMainToolbar = b
  }

  override fun getShowToolbarInNavigationBar(): Boolean {
    return !uiSettingsState().showMainToolbar && uiSettingsState().showNavigationBar
  }

  private fun uiSettingsState(): UISettingsState{
    if(uiStateSettings == null){
      uiStateSettings = UISettings.instance.state
    }
    return uiStateSettings!!
  }

}
