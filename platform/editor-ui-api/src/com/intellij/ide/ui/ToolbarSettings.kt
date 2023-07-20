// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ReportValue
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ToolbarSettings : PersistentStateComponent<ExperimentalToolbarSettingsState> {
  companion object {
    /*
     *     0 - indefinite
     *     1 - first inclusion
     *     >1 - not first inclusion
     */
    const val INCLUSION_STATE: String = "ide.widget.toolbar.first.inclusion"
    const val ROLLBACK_ACTION_ID: String = "RunToolbarRollbackToPrevious"

    @JvmStatic
    fun getInstance(): ToolbarSettings = ApplicationManager.getApplication().service<ToolbarSettings>()
  }

  val isAvailable: Boolean

  var isEnabled: Boolean

  var isVisible: Boolean
}

@ApiStatus.Internal
@ApiStatus.Experimental
class ExperimentalToolbarSettingsState : BaseState() {
  @get:ReportValue
  @get:OptionTag("SHOW_NEW_MAIN_TOOLBAR")
  var showNewMainToolbar: Boolean by property(false)
}
