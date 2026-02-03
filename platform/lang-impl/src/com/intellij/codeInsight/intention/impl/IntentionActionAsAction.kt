// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionSource
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.jetbrains.annotations.ApiStatus

/**
 * Wrapper for IntentionAction to allow assigning keyboard shortcuts to it
 *
 * The wrapper actions are created and managed by [IntentionShortcutManager].
 */
@ApiStatus.Internal
class IntentionActionAsAction(intention: IntentionAction)
  : AnAction({ CodeInsightBundle.message("intention.action.wrapper.name", intention.familyName) }) {

  private val actionId = intention.wrappedActionId

  override fun actionPerformed(e: AnActionEvent) {
    val intention = findIntention() ?: return

    val dataContext = e.dataContext
    val file = dataContext.getData(CommonDataKeys.PSI_FILE) ?: return
    val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return

    intention.invokeAsAction(editor, file, IntentionSource.CUSTOM_SHORTCUT)
  }

  override fun update(e: AnActionEvent) {
    val editor = e.dataContext.getData(CommonDataKeys.EDITOR) ?: return
    val file = e.dataContext.getData(CommonDataKeys.PSI_FILE) ?: return
    val project = e.project ?: return

    e.presentation.isEnabled = findIntention()?.isAvailable(project, editor, file) == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun findIntention(): IntentionAction? = IntentionShortcutManager.getInstance().findIntention(actionId)
}
