// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.experimental.meetNewUi

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.wm.ToolWindowId

/**
 * Hide default action of Meet New UI Toolwindow from default places like View -> Tool Windows
 */
class ActivateMeetNewUIToolWindowAction : ActivateToolWindowAction(ToolWindowId.MEET_NEW_UI) {

  override fun update(e: AnActionEvent) {
    e.presentation.text = IdeBundle.message("meetnewui.toolwindow.title", ApplicationNamesInfo.getInstance().fullProductName)
    e.presentation.isEnabledAndVisible = false
  }
}
