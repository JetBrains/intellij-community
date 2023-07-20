// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.demo.action

import com.intellij.feedback.demo.bundle.DemoFeedbackBundle
import com.intellij.feedback.demo.dialog.DemoFeedbackDialogWithEmail
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ShowDemoFeedbackDialogWithEmailAction : AnAction(DemoFeedbackBundle.message("show.demo.dialog.with.email.action.name")) {
  override fun actionPerformed(e: AnActionEvent) {
    DemoFeedbackDialogWithEmail(e.project, true).show()
  }
}