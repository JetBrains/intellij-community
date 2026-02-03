// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@State(name = "StructureViewState", storages = [(Storage(value = StoragePathMacros.WORKSPACE_FILE))])
@ApiStatus.Internal
class StructureViewState : PersistentStateComponent<StructureViewState> {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): StructureViewState = project.service()

    @JvmStatic
    fun getDefaultInstance(): StructureViewState = getInstance(ProjectManager.getInstance().defaultProject)
  }

  var selectedTab: String? = null

  override fun loadState(state: StructureViewState) {
    XmlSerializerUtil.copyBean(state, this)
  }

  override fun getState(): StructureViewState {
    return this
  }
}
