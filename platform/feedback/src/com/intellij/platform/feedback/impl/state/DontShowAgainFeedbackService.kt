// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl.state

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.*
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "DontShowAgainFeedbackService", storages = [Storage("DontShowAgainFeedbackService.xml")])
class DontShowAgainFeedbackService : PersistentStateComponent<DontShowAgainFeedbackState> {
  companion object {
    @JvmStatic
    fun checkIsAllowedToShowFeedback(): Boolean {
      return !getInstance().state.dontShowAgainIdeVersions.contains(ApplicationInfo.getInstance().shortVersion)
    }

    @JvmStatic
    fun dontShowFeedbackInCurrentVersion() {
      getInstance().state.dontShowAgainIdeVersions.add(ApplicationInfo.getInstance().shortVersion)
    }

    @JvmStatic
    fun getAllIdeVersionWithDisabledFeedback(): List<String> {
      return getInstance().state.dontShowAgainIdeVersions.toList()
    }

    private fun getInstance(): DontShowAgainFeedbackService = service()
  }

  private var state: DontShowAgainFeedbackState = DontShowAgainFeedbackState()

  override fun getState(): DontShowAgainFeedbackState {
    return state
  }

  override fun loadState(state: DontShowAgainFeedbackState) {
    this.state = state
  }
}

@Serializable
data class DontShowAgainFeedbackState(
  val dontShowAgainIdeVersions: MutableSet<String> = mutableSetOf()
)