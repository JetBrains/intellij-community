// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.new_ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.feedback.impl.IdleFeedbackTypes
import com.intellij.platform.feedback.new_ui.bundle.NewUIFeedbackBundle

private class TestShowNewUIFeedbackAction : AnAction(NewUIFeedbackBundle.message("test.action.name")) {
  override fun actionPerformed(e: AnActionEvent) {
    IdleFeedbackTypes.NEW_UI_FEEDBACK.showNotification(e.project, true)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}