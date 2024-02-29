// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.startup

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.feedback.startup.bundle.IdeStartupFeedbackMessagesBundle

class IdeStartupFeedbackShowAction : AnAction(IdeStartupFeedbackMessagesBundle.message("ide.startup.show.dialog.action.name")) {
  override fun actionPerformed(e: AnActionEvent) {
    IdeStartupFeedbackSurvey().showNotification(e.project!!, true)
  }
}