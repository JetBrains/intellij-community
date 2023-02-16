// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.localization.state

import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(name = "LocalizationFeedbackState", storages = [Storage("LocalizationFeedbackState.xml")])
class LocalizationFeedbackService : PersistentStateComponent<LocalizationFeedbackService.State> {
  companion object {
    fun getInstance() = service<LocalizationFeedbackService>()
  }

  private var myState: State? = null

  class State : BaseState() {

  }

  override fun getState() = myState

  override fun loadState(state: State) {
    myState = state
  }
}

// separate component just in case if we want to reuse this metric
@Service(Service.Level.APP)
@State(name = "IdeTimeUsageTracker", storages = [Storage("IdeTimeUsageTracker.xml")])
internal class IdeTimeUsageTrackerService : PersistentStateComponent<IdeTimeUsageTrackerService.State> {
  companion object {
    fun getInstance() = service<IdeTimeUsageTrackerService>()
  }

  private var myState: State? = null

  class State : BaseState() {

  }

  override fun getState() = myState

  override fun loadState(state: State) {
    myState = state
  }
}