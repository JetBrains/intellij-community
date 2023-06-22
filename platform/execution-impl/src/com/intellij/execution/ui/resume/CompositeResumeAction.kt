// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui.resume

import com.intellij.execution.ui.RunWidgetManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Key

class CompositeResumeAction : DumbAwareAction() {
  companion object {
    const val RESUME_ID = "Resume"
    const val PAUSE_ID = "Pause"
    const val DEFAULT_ACTION_ID = PAUSE_ID

    val RESUME_CURRENT_ACTION = Key.create<AnAction>("resume_current_action")
  }

  init {
    templatePresentation.icon = AllIcons.Actions.Pause
    templatePresentation.text = ""
  }

  private fun setDefaultState(presentation: Presentation) {
    ActionManager.getInstance().getAction(DEFAULT_ACTION_ID)?.let {
      updatePresentation(presentation, it.templatePresentation)
      presentation.isEnabled = false
      presentation.isVisible = true
    }
  }

  private fun updatePresentation(to: Presentation, from: Presentation) {
    to.text = from.text
    to.icon = from.icon
    to.description = from.description
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (!RunWidgetManager.getInstance(project).isResumeAvailable()) return

    e.presentation.getClientProperty(RESUME_CURRENT_ACTION)?.actionPerformed(e)

    setDefaultState(e.presentation)
  }

  private fun updateByAction(e: AnActionEvent, action: AnAction) {
    updatePresentation(e.presentation, action.templatePresentation)

    e.presentation.putClientProperty(RESUME_CURRENT_ACTION, action)

    action.update(e)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    val project = e.project ?: return


    if (!RunWidgetManager.getInstance(project).isResumeAvailable()) return

    val resume = ActionManager.getInstance().getAction(RESUME_ID) ?: return
    val pause = ActionManager.getInstance().getAction(PAUSE_ID) ?: return

    updateByAction(e, resume)

    if (e.presentation.isEnabled && e.presentation.isVisible) {
      return
    }

    updateByAction(e, pause)
    if (e.presentation.isEnabled && e.presentation.isVisible) {
      return
    }

    setDefaultState(e.presentation)

  }
}