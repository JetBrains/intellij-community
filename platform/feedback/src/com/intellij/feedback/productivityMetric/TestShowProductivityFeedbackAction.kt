// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.productivityMetric

import com.intellij.feedback.productivityMetric.bundle.ProductivityFeedbackBundle
import com.intellij.feedback.productivityMetric.dialog.ProductivityFeedbackDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class TestShowProductivityFeedbackAction : AnAction(ProductivityFeedbackBundle.message("test.action.name")) {
  override fun actionPerformed(e: AnActionEvent) {
    ProductivityFeedbackDialog(e.project, true).show()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}