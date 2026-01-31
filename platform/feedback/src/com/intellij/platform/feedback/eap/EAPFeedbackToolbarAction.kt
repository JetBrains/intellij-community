// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.eap

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

class EAPFeedbackToolbarAction : AnAction(), CustomComponentAction {

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

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE).apply {
      foreground = JBUI.CurrentTheme.RunWidget.FOREGROUND
    }
  }
}