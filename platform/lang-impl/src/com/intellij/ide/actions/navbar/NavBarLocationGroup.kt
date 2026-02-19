// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.navbar

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.ExperimentalUI

class NavBarLocationGroup : DefaultActionGroup(), DumbAware {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = ExperimentalUI.isNewUI()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}