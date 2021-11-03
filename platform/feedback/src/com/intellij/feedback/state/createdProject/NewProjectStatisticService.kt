// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.state.createdProject

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage


@State(name = "NewProjectInfoState", reloadable = true, storages = [Storage("NewProjectInfoState.xml")])
class NewProjectStatisticService : PersistentStateComponent<NewProjectInfoState> {

  private var state: NewProjectInfoState = NewProjectInfoState()

  override fun getState(): NewProjectInfoState {
    return state
  }

  override fun loadState(state: NewProjectInfoState) {
    this.state = state
  }
}