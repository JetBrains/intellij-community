// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.platform.lvcs.impl.DirectoryDiffMode
import com.intellij.util.EventDispatcher
import java.util.EventListener

@State(name = "Lvcs.Activity.App.Settings", storages = [Storage("lvcs.xml")], category = SettingsCategory.UI)
internal class ActivityViewApplicationSettings : SimplePersistentStateComponent<ActivityViewApplicationSettings.State>(State()) {
  private val eventDispatcher = EventDispatcher.create(Listener::class.java)

  class State : BaseState() {
    var diffMode by enum(DirectoryDiffMode.WithLocal)
    var showSystemLabels by property(true)
  }

  var diffMode: DirectoryDiffMode
    get() = state.diffMode
    set(value) {
      state.diffMode = value
      eventDispatcher.multicaster.settingsChanged()
    }

  var showSystemLabels: Boolean
    get() = state.showSystemLabels
    set(value) {
      state.showSystemLabels = value
      eventDispatcher.multicaster.settingsChanged()
    }

  fun addListener(listener: Listener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }

  interface Listener : EventListener {
    fun settingsChanged()
  }
}
