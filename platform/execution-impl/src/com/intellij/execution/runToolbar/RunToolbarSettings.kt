// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XCollection

@State(name = "RunToolbarSettings", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class RunToolbarSettings(private val project: Project) : SimplePersistentStateComponent<RunToolbarState>(RunToolbarState()) {
  companion object {
    fun getInstance(project: Project): RunToolbarSettings = project.service()
  }

  fun getRunConfigurations(): List<RunnerAndConfigurationSettings> {
    val runManager = RunManagerImpl.getInstanceImpl(project)
    return state.installedItems.mapNotNull { runManager.getConfigurationById(it) }.toMutableList()
  }

  fun setRunConfigurations(list : List<RunnerAndConfigurationSettings>) {
    state.installedItems.clear()
    state.installedItems.addAll(list.map { it.uniqueID }.toMutableList())
  }
}

class RunToolbarState : BaseState() {
  @get:XCollection
  val installedItems by list<String>()
}