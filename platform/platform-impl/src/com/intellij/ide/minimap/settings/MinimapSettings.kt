// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.settings

import com.intellij.ide.minimap.utils.WeakDelegate
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "Minimap", storages = [Storage(value = "Minimap.xml")])
class MinimapSettings : PersistentStateComponent<MinimapSettingsState> {

  enum class SettingsChangeType {
    Normal,
    WithUiRebuild
  }

  companion object {
    fun getInstance() = service<MinimapSettings>()
  }

  val settingsChangeCallback = WeakDelegate<SettingsChangeType, Unit>()

  private var state = MinimapSettingsState()

  override fun getState(): MinimapSettingsState = state
  fun setState(state: MinimapSettingsState) {
    this.state = state
  }

  override fun loadState(state: MinimapSettingsState) {
    try {
      XmlSerializerUtil.copyBean(state, this.state)
    }
    catch (e: Exception) {
      //
    }
  }
}