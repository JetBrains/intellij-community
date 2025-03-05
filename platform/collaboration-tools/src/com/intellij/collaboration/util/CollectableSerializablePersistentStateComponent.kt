// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.openapi.components.SerializablePersistentStateComponent
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class CollectableSerializablePersistentStateComponent<T : Any>(defaultState: T)
  : SerializablePersistentStateComponent<T>(defaultState) {
  protected val stateFlow: MutableStateFlow<T> = MutableStateFlow(defaultState)

  override fun loadState(state: T) {
    super.loadState(state)
    stateFlow.value = state
  }

  protected fun updateStateAndEmit(updateFunction: (currentState: T) -> T) {
    updateState(updateFunction)
    stateFlow.value = state
  }
}