// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class TestShowNewProjectFeedbackDialogAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    FeedbackTypes.PROJECT_CREATION_FEEDBACK.showNotification(e.project, true)
  }
}