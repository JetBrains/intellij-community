// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.state

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage


@State(name = "DontShowAgainFeedbackService", reloadable = true, storages = [Storage("DontShowAgainFeedbackService.xml")])
class DontShowAgainFeedbackService : PersistentStateComponent<DontShowAgainFeedbackState> {

  companion object {
    @JvmStatic
    fun getInstance(): DontShowAgainFeedbackService {
      return ApplicationManager.getApplication().getService(DontShowAgainFeedbackService::class.java)
    }
  }

  private var state: DontShowAgainFeedbackState = DontShowAgainFeedbackState()

  override fun getState(): DontShowAgainFeedbackState {
    return state
  }

  override fun loadState(state: DontShowAgainFeedbackState) {
    this.state = state
  }
}