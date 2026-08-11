package com.intellij.platform.ijent.community.ui.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.platform.eel.EelMachine

internal abstract class PerMachineSettingsBase<T, STATE> : PersistentStateComponent<STATE> where STATE : PerMachineSettingsBase.PerMachineState<T> {

  interface PerMachineState<T> {
    var data: MutableMap<String, T>
  }

  protected abstract var myState: STATE
  override fun getState(): STATE = myState
  override fun loadState(newState: STATE) { myState = newState }
  abstract fun createDefault(machine: EelMachine): T

  @Synchronized
  fun get(machine: EelMachine): T {
    val key = machine.internalName
    val map = myState.data
    return map[key] ?: createDefault(machine).also { map[key] = it }
  }
}
