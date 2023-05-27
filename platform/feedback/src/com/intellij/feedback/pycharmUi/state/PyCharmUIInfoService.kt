// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.pycharmUi.state

import com.intellij.openapi.components.*
import kotlinx.datetime.*
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "PyCharmUIInfoState",
       storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE, deprecated = true), Storage("PyCharmUIInfoState.xml")])
class PyCharmUIInfoService : PersistentStateComponent<PyCharmUIInfoState> {
  companion object {
    @JvmStatic
    fun getInstance(): PyCharmUIInfoService = service()
  }

  private var state = PyCharmUIInfoState()

  override fun getState(): PyCharmUIInfoState = state

  override fun loadState(state: PyCharmUIInfoState) {
    this.state = state
  }

}

@Serializable
data class PyCharmUIInfoState(
  var numberNotificationShowed: Int = 0,
  var feedbackSent: Boolean = false,
  var newUserFirstRunDate: LocalDateTime? = null
)