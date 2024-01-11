// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.actions

import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.Project
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.RevisionSelection
import com.intellij.platform.lvcs.impl.operations.createReverter

class RevertAction : RevisionSelectionAction() {

  override fun actionPerformed(project: Project, gateway: IdeaGateway, activityScope: ActivityScope, selection: RevisionSelection) {
    val facade = LocalHistoryImpl.getInstanceImpl().facade ?: return
    val reverter = activityScope.createReverter(project, facade, gateway, selection)
    if (reverter.checkCanRevert().isNotEmpty()) return
    reverter.revert()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}