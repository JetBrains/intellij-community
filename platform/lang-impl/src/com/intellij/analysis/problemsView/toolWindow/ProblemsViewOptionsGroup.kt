// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.ExperimentalUI

/**
 * @author Konstantin Bulenkov
 */
class ProblemsViewOptionsGroup: DefaultActionGroup() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    if (ExperimentalUI.isNewUI()) {
      e.presentation.icon = AllIcons.Actions.GroupBy
    }
  }
}