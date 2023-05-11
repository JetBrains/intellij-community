// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.aqua

import com.intellij.feedback.aqua.bundle.AquaFeedbackBundle
import com.intellij.feedback.common.IdleFeedbackTypes
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class TestShowAquaNewUserFeedbackAction : AnAction(AquaFeedbackBundle.message("new.user.test.action.name")) {
  override fun actionPerformed(e: AnActionEvent) {
    IdleFeedbackTypes.AQUA_NEW_USER_FEEDBACK.showNotification(e.project, true)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}