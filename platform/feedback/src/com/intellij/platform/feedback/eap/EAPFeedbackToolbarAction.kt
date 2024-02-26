// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.eap

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class EAPFeedbackToolbarAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    setEAPFeedbackShown()
    executeEAPFeedbackAction()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val isVisible = isEAPEnv() && isEAPFeedbackAvailable()
    presentation.isEnabledAndVisible = isVisible
    if (isVisible) {
      presentation.icon = AllIcons.Ide.FeedbackRatingOn
      presentation.text = EAPFeedbackBundle.message("action.EAPFeedbackToolbarAction.text")
      presentation.description = EAPFeedbackBundle.message("action.EAPFeedbackToolbarAction.description")
    }
  }

  override fun displayTextInToolbar(): Boolean = true
}