// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.miniMap.settings

import com.intellij.ide.miniMap.utils.WeakDelegate
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "MiniMap", storages = [Storage(value = "MiniMap.xml")])
class MiniMapSettings : PersistentStateComponent<MiniMapSettingsState> {

  enum class SettingsChangeType {
    Normal,
    WithUiRebuild
  }

  companion object {
    fun getInstance() = service<MiniMapSettings>()
  }

  val settingsChangeCallback = WeakDelegate<SettingsChangeType, Unit>()

  private var state = MiniMapSettingsState()

  override fun getState(): MiniMapSettingsState = state
  fun setState(state: MiniMapSettingsState) {
    this.state = state
  }

  override fun loadState(state: MiniMapSettingsState) {
    try {
      XmlSerializerUtil.copyBean(state, this.state)
    }
    catch (e: Exception) {
      //
    }
  }
}