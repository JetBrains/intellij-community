package com.intellij.ide

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.IdeUICustomization

abstract class IdeDependentAction : DumbAwareAction() {

  private val id by lazy { ActionManager.getInstance().getId(this) }

  override fun update(e: AnActionEvent) {
    super.update(e)
    IdeUICustomization.getInstance().getActionText(id)?.let {
      e.presentation.text = it
    }
    IdeUICustomization.getInstance().getActionDescription(id)?.let {
      e.presentation.description = it
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun isDumbAware() = true
}