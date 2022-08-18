// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.npw

import com.intellij.feedback.common.IdleFeedbackTypes
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

private class TestShowNewProjectFeedbackDialogAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    IdleFeedbackTypes.PROJECT_CREATION_FEEDBACK.showNotification(e.project, true)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}