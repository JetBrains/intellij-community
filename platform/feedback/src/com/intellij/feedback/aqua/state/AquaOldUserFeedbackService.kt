// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.aqua.state

import com.intellij.openapi.components.*
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "AquaOldUserFeedbackInfoState", storages = [Storage("AquaOldUserFeedbackService.xml")])
class AquaOldUserFeedbackService : PersistentStateComponent<AquaNewUserInfoState> {
  companion object {
    @JvmStatic
    fun getInstance(): AquaOldUserFeedbackService = service()
  }

  private var state = AquaNewUserInfoState()

  override fun getState(): AquaNewUserInfoState = state

  override fun loadState(state: AquaNewUserInfoState) {
    this.state = state
  }
}

@Serializable
data class AquaOldUserInfoState(
  var numberNotificationShowed: Int = 0,
  var feedbackSent: Boolean = false,
)