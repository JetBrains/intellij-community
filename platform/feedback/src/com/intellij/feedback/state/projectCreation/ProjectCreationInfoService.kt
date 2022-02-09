// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.state.projectCreation

import com.intellij.openapi.components.*
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "ProjectCreationInfoState", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
class ProjectCreationInfoService : PersistentStateComponent<ProjectCreationInfoState> {
  companion object {
    @JvmStatic
    fun getInstance(): ProjectCreationInfoService = service()
  }

  private var state = ProjectCreationInfoState()

  override fun getState(): ProjectCreationInfoState = state

  override fun loadState(state: ProjectCreationInfoState) {
    this.state = state
  }
}

@Serializable
data class ProjectCreationInfoState(
  var numberNotificationShowed: Int = 0,
  var feedbackSent: Boolean = false,
  var lastCreatedProjectBuilderId: String? = null
)