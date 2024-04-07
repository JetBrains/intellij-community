// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.twnames

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

internal class TwNamesFeedbackShowAction : AnAction(TwNamesFeedbackMessagesBundle.message("tw.names.show.dialog.action.name")) {
  override fun actionPerformed(e: AnActionEvent) {
    TwNamesSurvey().showNotification(e.project!!, true)
  }
}