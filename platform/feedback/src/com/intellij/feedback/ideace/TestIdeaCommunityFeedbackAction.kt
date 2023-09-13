// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.ideace

import com.intellij.feedback.common.IdleFeedbackTypes
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

private class TestIdeaCommunityFeedbackAction : AnAction(IdeaCommunityFeedbackBundle.message("test.action.name")) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    IdleFeedbackTypes.IDEA_CE_FEEDBACK.showNotification(e.project, true)
  }
}