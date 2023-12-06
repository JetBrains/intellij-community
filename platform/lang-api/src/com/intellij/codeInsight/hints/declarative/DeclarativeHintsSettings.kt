// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock

@State(name = "DeclarativeInlayHintsSettings", storages = [Storage("editor.xml")], category = SettingsCategory.CODE)
class DeclarativeInlayHintsSettings : SimplePersistentStateComponent<DeclarativeInlayHintsSettings.HintsState>(
  HintsState()) {

  class HintsState : BaseState() {
    // Format: providerId + # + optionId
    var disabledOptions: MutableSet<String> by stringSet()
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
    if (getSerializedId(providerId, optionId) in state.disabledOptions) return false
    return null
  }

  private fun getSerializedId(providerId: String, optionId: String) = "$providerId#$optionId"

  @RequiresWriteLock
  fun setOptionEnabled(optionId: String, providerId: String, isEnabled: Boolean) {
    val serializedId = getSerializedId(providerId, optionId)
    val areSame = state.disabledOptions.contains(serializedId) != isEnabled
    if (!isEnabled) {
      state.disabledOptions.add(serializedId)
    } else {
      state.disabledOptions.remove(serializedId)
    }
    if (!areSame) {
      state.intIncrementModificationCount()
    }
  }

  @RequiresReadLock
  fun isProviderEnabled(providerId: String): Boolean? {
    return state.providerIdToEnabled[providerId]
  }

  @RequiresWriteLock
  fun setProviderEnabled(providerId: String, value: Boolean) {
    val previousState = state.providerIdToEnabled.put(providerId, value)
    if (previousState != value) {
      state.intIncrementModificationCount()
    }
  }
}