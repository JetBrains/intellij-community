// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "NotRoamableUiSettings", storages = [(Storage(Storage.NOT_ROAMABLE_FILE))], reportStatistic = true)
class NotRoamableUiSettings : PersistentStateComponent<NotRoamableUiOptions> {
  private var state = NotRoamableUiOptions()

  override fun getState() = state

  override fun loadState(state: NotRoamableUiOptions) {
    this.state = state
  }
}

class NotRoamableUiOptions : BaseState() {
  var ideAAType by property(AntialiasingType.SUBPIXEL)

  var editorAAType by property(AntialiasingType.SUBPIXEL)
}