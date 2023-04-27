// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.aqua.state

import com.intellij.openapi.components.*
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "AquaNewUserFeedbackInfoState", storages = [Storage("AquaNewUserFeedbackService.xml")])
class AquaNewUserFeedbackService : PersistentStateComponent<AquaOldUserInfoState> {
  companion object {
    @JvmStatic
    fun getInstance(): AquaNewUserFeedbackService = service()
  }

  private var state = AquaOldUserInfoState()

  override fun getState(): AquaOldUserInfoState = state

  override fun loadState(state: AquaOldUserInfoState) {
    this.state = state
  }
}

@Serializable
data class AquaNewUserInfoState(
  var numberNotificationShowed: Int = 0,
  var feedbackSent: Boolean = false,
)