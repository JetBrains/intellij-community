// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.messages.Topic

@State(name = "ProjectConfigurationDirectoryViewState", storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))], category = SettingsCategory.UI)
open class ProjectConfigurationDirectoryViewState @JvmOverloads @NonInjectable constructor(initialState: State = State())
  : SimplePersistentStateComponent<ProjectConfigurationDirectoryViewState.State>(initialState) {

  var shouldShow: Boolean
    get() = state.shouldShow
    set(value) {
      val oldValue = state.shouldShow
      state.shouldShow = value

      if (oldValue != value) {
        ApplicationManager.getApplication().messageBus.syncPublisher(Listener.TOPIC).changed()
      }
    }

  class State(defaultShow: Boolean = false) : BaseState() {
    var shouldShow by property(defaultShow)
  }

  interface Listener {
    fun changed()

    companion object {
      @JvmField
      val TOPIC = Topic(Listener::class.java)
    }
  }
}
