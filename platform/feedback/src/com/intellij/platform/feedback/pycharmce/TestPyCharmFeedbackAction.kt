// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.pycharmce

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.feedback.impl.IdleFeedbackTypes

private class TestPyCharmFeedbackAction : AnAction(PyCharmCeFeedbackBundle.message("test.action.name")) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    IdleFeedbackTypes.PYCHARM_CE_FEEDBACK.showNotification(e.project, true)
  }
}