// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal.shared

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList

@ApiStatus.Internal
@Service(Service.Level.APP)
@State(name = JavaTerminalSettings.COMPONENT_NAME, storages = [Storage(value = "terminal.xml")])
class JavaTerminalSettings : PersistentStateComponent<JavaTerminalSettings.State> {
  private val listeners = CopyOnWriteArrayList<JavaTerminalSettingsListener>()

  companion object {
    val instance: JavaTerminalSettings
      get() = service<JavaTerminalSettings>()

    const val COMPONENT_NAME: String = "JavaTerminalSettings"
  }

  fun addListener(listener: JavaTerminalSettingsListener) {
    listeners.addIfAbsent(listener)
  }

  fun removeListener(listener: JavaTerminalSettingsListener) {
    listeners.remove(listener)
  }

  private fun fireSettingsChanged() {
    for (listener in listeners) {
      listener.settingsChanged()
    }
  }

  private var state: State = State()

  var overrideJavaHome: Boolean
    get() = state.overrideJavaHome
    set(value) {
      if (value != state.overrideJavaHome) {
        state.overrideJavaHome = value
        fireSettingsChanged()
      }
    }

  override fun getState(): JavaTerminalSettings.State {
    return state
  }

  override fun loadState(state: JavaTerminalSettings.State) {
    val oldState = this.state
    this.state = state
    // Fire listeners in loadState as well because it is used by the platform logic to deliver remote changes.
    if (state.overrideJavaHome != oldState.overrideJavaHome) {
      fireSettingsChanged()
    }
  }

  override fun noStateLoaded() {
    // Required for the RemDev case: when remote settings are changed to the defaults, platform calls `noStateLoaded` instead of `loadState`.
    loadState(State())
  }

  class State {
    var overrideJavaHome: Boolean = true
  }
}

@ApiStatus.Internal
fun interface JavaTerminalSettingsListener {
  fun settingsChanged()
}