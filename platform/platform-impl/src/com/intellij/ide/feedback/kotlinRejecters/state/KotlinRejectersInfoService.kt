// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.feedback.kotlinRejecters.state

import com.intellij.openapi.components.*
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "KotlinRejectersInfoService", storages = [Storage("KotlinRejectersInfoService.xml")])
class KotlinRejectersInfoService : PersistentStateComponent<KotlinRejectersInfoState> {
  companion object {
    @JvmStatic
    fun getInstance(): KotlinRejectersInfoService = service()
  }

  private var state = KotlinRejectersInfoState()

  override fun getState(): KotlinRejectersInfoState = state

  override fun loadState(state: KotlinRejectersInfoState) {
    this.state = state
  }
}

@Serializable
data class KotlinRejectersInfoState(
  var feedbackSent: Boolean = false,
  var showNotificationAfterRestart: Boolean = false,
  var numberNotificationShowed: Int = 0
)