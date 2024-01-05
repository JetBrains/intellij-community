// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.actions

import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.ChangeSetSelection
import com.intellij.platform.lvcs.impl.toChangeSetSelection
import com.intellij.platform.lvcs.impl.ui.ActivityViewDataKeys

abstract class ChangeSetSelectionAction : DumbAwareAction() {
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
    e.presentation.isEnabled = activitySelection.toChangeSetSelection() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val activityScope = e.getRequiredData(ActivityViewDataKeys.SCOPE)
    val activitySelection = e.getRequiredData(ActivityViewDataKeys.SELECTION)

    val localHistoryImpl = LocalHistoryImpl.getInstanceImpl()
    val facade = localHistoryImpl.facade ?: return
    val gateway = localHistoryImpl.gateway ?: return

    val selection = activitySelection.toChangeSetSelection() ?: return

    actionPerformed(project, facade, gateway, activityScope, selection)
  }

  abstract fun actionPerformed(project: Project, facade: LocalHistoryFacade, gateway: IdeaGateway,
                               activityScope: ActivityScope, selection: ChangeSetSelection)
}