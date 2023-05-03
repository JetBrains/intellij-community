// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.experimental.meetNewUi

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.ExperimentalUI

class ActivateMeetNewUIToolWindowAction : ActivateToolWindowAction(ToolWindowId.MEET_NEW_UI) {

  override fun update(e: AnActionEvent) {
    if (ExperimentalUI.isNewUI() && Registry.`is`("ide.experimental.ui.meetNewUi")) {
      super.update(e)
      e.presentation.text = ActionsBundle.message("action.ActivateMeetNewUIToolWindow.text")
      e.presentation.isVisible = e.place == ActionPlaces.MAIN_MENU
      if (SystemInfoRt.isMac) {
        e.presentation.icon = null
      }
    }
    else {
      e.presentation.isEnabledAndVisible = false
    }
  }
}
