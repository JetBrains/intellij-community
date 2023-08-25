// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.ideace

import com.intellij.openapi.components.*
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "NewUIInfoState",
       storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE, deprecated = true), Storage("NewUIInfoService.xml")])
class IdeaCommunityFeedbackService : PersistentStateComponent<IdeaCommunityFeedbackState> {
  companion object {
    @JvmStatic
    fun getInstance(): IdeaCommunityFeedbackService = service()
  }

  private var state = IdeaCommunityFeedbackState()

  override fun getState(): IdeaCommunityFeedbackState = state

  override fun loadState(state: IdeaCommunityFeedbackState) {
    this.state = state
  }
}

@Serializable
data class IdeaCommunityFeedbackState(
  var numberNotificationShowed: Int = 0,
  var feedbackSent: Boolean = false
)