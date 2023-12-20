// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.actions

import com.intellij.history.integration.LocalHistoryImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.lvcs.impl.createReverter
import com.intellij.platform.lvcs.impl.toRevisionSelection
import com.intellij.platform.lvcs.impl.ui.ActivityViewDataKeys

class RevertAction : RevisionSelectionAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val activityScope = e.getRequiredData(ActivityViewDataKeys.SCOPE)
    val activitySelection = e.getRequiredData(ActivityViewDataKeys.SELECTION)

    val facade = LocalHistoryImpl.getInstanceImpl().facade ?: return
    val gateway = LocalHistoryImpl.getInstanceImpl().gateway ?: return
    val selection = activitySelection.toRevisionSelection(activityScope) ?: return

    val reverter = activityScope.createReverter(project, facade, gateway, selection)
    if (reverter.checkCanRevert().isNotEmpty()) return
    reverter.revert()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}