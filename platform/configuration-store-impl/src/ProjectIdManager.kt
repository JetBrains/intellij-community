// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly

@Internal
internal interface ProjectIdManager {

  companion object {

    fun getInstance(project: Project): ProjectIdManager = project.service()
  }

  var id: @NonNls String?
}

@State(name = "ProjectId", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))], reportStatistic = false)
private class ProjectIdManagerImpl : SimplePersistentStateComponent<ProjectIdState>(ProjectIdState()),
                                     ProjectIdManager {

  override var id: @NonNls String?
    get() = state.id
    set(value) {
      state.id = value
    }
}

internal class ProjectIdState : BaseState() {
  @get:Attribute
  var id by string()
}

@TestOnly
private class MockProjectIdManager : ProjectIdManager {

  override var id: String? = null
}