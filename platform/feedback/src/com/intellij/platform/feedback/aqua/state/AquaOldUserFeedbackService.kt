// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.aqua.state

import com.intellij.openapi.components.*
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "AquaOldUserFeedbackInfoState", storages = [Storage("AquaOldUserFeedbackService.xml")])
class AquaOldUserFeedbackService : PersistentStateComponent<AquaOldUserInfoState> {
  companion object {
    @JvmStatic
    fun getInstance(): AquaOldUserFeedbackService = service()
  }

  private var state = AquaOldUserInfoState()

  override fun getState(): AquaOldUserInfoState = state

  override fun loadState(state: AquaOldUserInfoState) {
    this.state = state
  }
}

@Serializable
data class AquaOldUserInfoState(
  var numberNotificationShowed: Int = 0,
  var feedbackSent: Boolean = false,
  var userTypedInEditor: Boolean = false,
  var firstUsageTime: LocalDateTime? = null
)