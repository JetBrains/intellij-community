// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@State(name = "UnlinkedProjectSettings")
class UnlinkedProjectSettings :
  SimplePersistentStateComponent<UnlinkedProjectSettings.State>(State()),
  ExternalSystemUnlinkedProjectSettings {

  override var isEnabledAutoLink: Boolean
    get() = state.isEnabledAutoLink
    set(value) {
      state.isEnabledAutoLink = value
    }

  class State : BaseState() {
    var isEnabledAutoLink by property(true)
  }
}