// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute

@Service(Service.Level.PROJECT)
@State(name = "ProjectId", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))], reportStatistic = false)
internal class ProjectIdManager : SimplePersistentStateComponent<ProjectIdState>(ProjectIdState()) {
  companion object {
    fun getInstance(project: Project): ProjectIdManager = project.service()
  }

  var id: String?
    get() = state.id
    set(value) {
      state.id = value
    }
}

internal class ProjectIdState : BaseState() {
  @get:Attribute
  var id by string()
}