// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings.HintsState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock

@State(name = "DeclarativeInlayHintsSettings", storages = [Storage("editor.xml")], category = SettingsCategory.CODE)
class DeclarativeInlayHintsSettings : SimplePersistentStateComponent<HintsState>(HintsState()) {

  class HintsState : BaseState() {
    // Format: providerId + # + optionId
    var enabledOptions: MutableMap<String, Boolean> by map()
    var providerIdToEnabled: MutableMap<String, Boolean> by map()
  }


  companion object {
    fun getInstance(): DeclarativeInlayHintsSettings {
      return ApplicationManager.getApplication().service()
    }
  }

  /**
   * Note that it may return true even if the provider is enabled!
   */
  @RequiresReadLock
  fun isOptionEnabled(optionId: String, providerId: String): Boolean? {
    val serializedId = getSerializedId(providerId, optionId)
    return state.enabledOptions[serializedId]
  }

  private fun getSerializedId(providerId: String, optionId: String) = "$providerId#$optionId"

  @RequiresWriteLock
  fun setOptionEnabled(optionId: String, providerId: String, isEnabled: Boolean) {
    val serializedId = getSerializedId(providerId, optionId)
    val previousState = state.enabledOptions.put(serializedId, isEnabled)
    if (previousState != isEnabled) {
      state.intIncrementModificationCount()
    }
  }

  @RequiresReadLock
  fun isProviderEnabled(providerId: String): Boolean? {
    return state.providerIdToEnabled[providerId]
  }

  @RequiresWriteLock
  fun setProviderEnabled(providerId: String, isEnabled: Boolean) {
    val previousState = state.providerIdToEnabled.put(providerId, isEnabled)
    if (previousState != isEnabled) {
      state.intIncrementModificationCount()
    }
  }
}