// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
@State(name = "RunToolbarSettings", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class RunToolbarSettings(private val project: Project) : SimplePersistentStateComponent<RunToolbarState>(RunToolbarState()) {
  companion object {
    fun getInstance(project: Project): RunToolbarSettings = project.service()
  }

  fun setConfigurations(map: Map<String, RunnerAndConfigurationSettings?>, slotOrder: List<String>) {
    val slt = mutableMapOf<String, String>()
    map.filter { it.value != null }.forEach{entry ->  entry.value?.let {
      slt[entry.key] = it.uniqueID
    }}

    state.slots.clear()
    state.slots.putAll(slt)

    state.slotOrder.clear()
    state.slotOrder.addAll(slotOrder)
  }


  fun getConfigurations(): Pair<List<String>, Map<String, RunnerAndConfigurationSettings>> {
    val runManager = RunManagerImpl.getInstanceImpl(project)

    var slotOrder = mutableListOf<String>()
    val slt = mutableMapOf<String, RunnerAndConfigurationSettings>()

    if(state.slots.isEmpty() || state.slotOrder.isEmpty()) {
      state.installedItems.mapNotNull { runManager.getConfigurationById(it) }.forEach {
        val uid = UUID.randomUUID().toString()
        slotOrder.add(uid)
        slt[uid] = it
      }
    } else {
      state.slots.forEach { entry ->
        runManager.getConfigurationById(entry.value)?.let {
          slt[entry.key] = it
        }
      }
      slotOrder = state.slotOrder
    }

    return Pair(slotOrder, slt)
  }

  fun getMoveNewOnTop(): Boolean {
    return state.moveNewOnTop
  }

  fun setMoveNewOnTop(value: Boolean) {
    state.moveNewOnTop = value
  }

  fun getUpdateMainBySelected(): Boolean {
    return state.updateMainBySelected
  }

  fun setUpdateMainBySelected(value: Boolean) {
    state.updateMainBySelected = value
  }

  fun setRunConfigWidth(value: Int) {
    state.runConfigWidth = value
  }

  fun getRunConfigWidth(): Int {
    return state.runConfigWidth
  }
}

@ApiStatus.Internal
class RunToolbarState : BaseState() {
  @Deprecated("Use slots map instead of installedItems")
  @get:XCollection
  val installedItems by list<String>()

  @get:XCollection
  val slotOrder by list<String>()

  @get:XMap
  var slots by map<String, String>()

  var moveNewOnTop by property(defaultValue = true)
  var updateMainBySelected by property(defaultValue = true)
  var runConfigWidth by property(defaultValue = RunWidgetWidthHelper.RUN_CONFIG_WIDTH_UNSCALED_MIN)
}