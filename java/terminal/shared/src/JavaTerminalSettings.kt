// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal.shared

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList

@ApiStatus.Internal
@Service(Service.Level.APP)
@State(name = "JavaTerminalSettings", storages = [Storage(value = "other.xml", roamingType = RoamingType.DISABLED)])
class JavaTerminalSettings : SerializablePersistentStateComponent<JavaTerminalSettings.State>(State()) {
  private val listeners = CopyOnWriteArrayList<JavaTerminalSettingsListener>()

  companion object {
    val instance: JavaTerminalSettings
      get() = service<JavaTerminalSettings>()
  }

  fun addListener(listener: JavaTerminalSettingsListener) {
    listeners.addIfAbsent(listener)
  }

  fun removeListener(listener: JavaTerminalSettingsListener) {
    listeners.remove(listener)
  }

  private fun fireSettingsChanged(oldState: State, newState: State) {
    for (listener in listeners) {
      listener.settingsChanged(oldState, newState)
    }
  }

  var overrideJavaHome: Boolean
    get() = state.overrideJavaHome
    set(value) {
      val oldState = state
      val newState = updateState { it.copy(overrideJavaHome = value) }
      if (oldState != newState) {
        fireSettingsChanged(oldState, newState)
      }
    }

  @Serializable
  data class State(
    @JvmField val overrideJavaHome: Boolean = true,
  )
}

@ApiStatus.Internal
fun interface JavaTerminalSettingsListener {
  fun settingsChanged(oldState: JavaTerminalSettings.State, newState: JavaTerminalSettings.State)
}