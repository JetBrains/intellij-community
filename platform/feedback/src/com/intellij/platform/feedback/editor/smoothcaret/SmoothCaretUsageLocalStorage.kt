// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.editor.smoothcaret

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service
@State(name = "SmoothCaretFeedback", storages = [Storage("smoothCaretFeedback.xml")])
class SmoothCaretUsageLocalStorage : PersistentStateComponent<SmoothCaretUsageLocalStorage.State> {

  data class State(
    var feedbackNotificationShown: Boolean = false,
    var feedbackNotificationShownTime: Long = 0L,
  )

  private var state = State()

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
  }

  fun recordFeedbackNotificationShown() {
    state.feedbackNotificationShown = true
    state.feedbackNotificationShownTime = System.currentTimeMillis()
  }

  companion object {
    @JvmStatic
    fun getInstance(): SmoothCaretUsageLocalStorage {
      return ApplicationManager.getApplication().getService(SmoothCaretUsageLocalStorage::class.java)
    }
  }
}
