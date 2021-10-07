// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ToolbarSettings : PersistentStateComponent<ExperimentalToolbarSettingsState> {

  companion object {

    @JvmStatic
    val Instance: ToolbarSettings
      get() = ApplicationManager.getApplication().getService(ToolbarSettings::class.java)
  }

  var isEnabled: Boolean

  var isVisible: Boolean
}

@ApiStatus.Internal
@ApiStatus.Experimental
class ExperimentalToolbarSettingsState : BaseState() {

  @get:OptionTag("SHOW_NEW_MAIN_TOOLBAR")
  var showNewMainToolbar by property(false)
}
