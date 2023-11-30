// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.pycharmce

import com.intellij.openapi.components.*
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "PyCharmCEFeedbackState",
       storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE, deprecated = true), Storage("PyCharmCEFeedbackService.xml")])
class PyCharmCeFeedbackService : PersistentStateComponent<PyCharmCeFeedbackState> {
  companion object {
    @JvmStatic
    fun getInstance(): PyCharmCeFeedbackService = service()
  }

  private var state = PyCharmCeFeedbackState()

  override fun getState(): PyCharmCeFeedbackState = state

  override fun loadState(state: PyCharmCeFeedbackState) {
    this.state = state
  }
}

@Serializable
data class PyCharmCeFeedbackState(
  var numberNotificationShowed: Int = 0,
  var feedbackSent: Boolean = false
)