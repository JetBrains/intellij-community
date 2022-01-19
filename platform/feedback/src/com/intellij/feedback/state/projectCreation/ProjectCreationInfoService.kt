// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.state.projectCreation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage


@State(name = "ProjectCreationInfoState", reloadable = true, storages = [Storage("ProjectCreationInfoState.xml")])
class ProjectCreationInfoService : PersistentStateComponent<ProjectCreationInfoState> {

  companion object {
    @JvmStatic
    fun getInstance(): ProjectCreationInfoService {
      return ApplicationManager.getApplication().getService(ProjectCreationInfoService::class.java)
    }
  }

  private var state: ProjectCreationInfoState = ProjectCreationInfoState()

  override fun getState(): ProjectCreationInfoState {
    return state
  }

  override fun loadState(state: ProjectCreationInfoState) {
    this.state = state
  }
}