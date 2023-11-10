// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.lvcs.impl.toRevisionSelection
import com.intellij.platform.lvcs.ui.ActivityViewDataKeys

abstract class RevisionSelectionAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    if (e.project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val activitySelection = e.getData(ActivityViewDataKeys.SELECTION)
    val activityScope = e.getData(ActivityViewDataKeys.SCOPE)
    if (activityScope == null || activitySelection == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isVisible = true
    e.presentation.isEnabled = activitySelection.toRevisionSelection(activityScope) != null
  }
}