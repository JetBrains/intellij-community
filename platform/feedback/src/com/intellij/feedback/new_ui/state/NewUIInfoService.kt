// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.new_ui.state

import com.intellij.openapi.components.*
import com.intellij.openapi.util.registry.Registry
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "NewUIInfoState",
       storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE, deprecated = true), Storage("NewUIInfoService.xml")])
class NewUIInfoService : PersistentStateComponent<NewUIInfoState> {
  companion object {
    @JvmStatic
    fun getInstance(): NewUIInfoService = service()
  }

  private var state = NewUIInfoState()

  override fun getState(): NewUIInfoState = state

  override fun loadState(state: NewUIInfoState) {
    this.state = state
  }

  fun updateEnableNewUIDate() {
    if (state.enableNewUIDate == null) {
      state.enableNewUIDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
  }

  fun updateDisableNewUIDate() {
    if (state.disableNewUIDate == null) {
      state.disableNewUIDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
  }
}

@Serializable
data class NewUIInfoState(
  var numberNotificationShowed: Int = 0,
  var feedbackSent: Boolean = false,
  var enableNewUIDate: LocalDateTime? = if (Registry.get("ide.experimental.ui").asBoolean())
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
  else null,
  var disableNewUIDate: LocalDateTime? = null
)