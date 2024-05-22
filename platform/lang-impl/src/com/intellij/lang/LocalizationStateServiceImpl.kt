// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang

import com.intellij.l10n.LocalizationState
import com.intellij.l10n.LocalizationStateService
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@State(name = "LocalizationStateService",  storages = [Storage("LocalizationStateService.xml")])
class LocalizationStateServiceImpl : LocalizationStateService, PersistentStateComponent<LocalizationState> {
  init {
    LocalizationUtil.isL10nInitialized = true
  }
   private var localizationState = LocalizationState()

  override fun getState(): LocalizationState {
    return localizationState
  }

  override fun loadState(state: LocalizationState) {
    this.localizationState = state
  }

  override fun getLocalizationState(): LocalizationState {
    return state
  }

}

