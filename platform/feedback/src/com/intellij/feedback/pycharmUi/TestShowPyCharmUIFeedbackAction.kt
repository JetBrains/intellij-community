// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.pycharmUi

import com.intellij.feedback.common.IdleFeedbackTypes
import com.intellij.feedback.pycharmUi.bundle.PyCharmUIFeedbackBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

private class TestShowNewUIFeedbackAction : AnAction(PyCharmUIFeedbackBundle.message("test.action.name")) {
  override fun actionPerformed(e: AnActionEvent) {
    IdleFeedbackTypes.PYCHARM_UI_FEEDBACK.showNotification(e.project, true)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}