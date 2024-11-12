// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.core.CommandCompletionService
import com.intellij.codeInsight.completion.commands.api.OldCommand
import com.intellij.codeInsight.completion.commands.core.PREVIOUS_HIGHLIGHT_CACHED_CONTAINER_INFO_CONTAINER_KEY
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.daemon.impl.quickfix.ExpensivePsiIntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.modcommand.ModHighlight
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import kotlinx.coroutines.launch
import javax.swing.Icon

class IntentionCommand(
  private val intentionAction: IntentionActionWithTextCaching,
  override val priority: Int?,
  override val icon: Icon?,
  override val highlightInfo: ModHighlight.HighlightInfo?,
) : OldCommand() {

  override val name: String
    get() = intentionAction.text

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (editor == null) return false
    return intentionAction.action !is ExpensivePsiIntentionAction && runReadAction {
      val available = intentionAction.action.isAvailable(psiFile.project, editor, psiFile)
      available
    }
  }

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    if (editor == null) return
    val project = editor.project ?: return
    ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, intentionAction.action, name)
    val service = psiFile.project.service<CommandCompletionService>()
    service.coroutineScope.launch {
      readAction {
        val data = editor.getUserData(PREVIOUS_HIGHLIGHT_CACHED_CONTAINER_INFO_CONTAINER_KEY)
        if (data != null) {
          for (infoContainer in data) {
            infoContainer.highlighters.errorFixes.remove(intentionAction)
            infoContainer.highlighters.inspectionFixes.remove(intentionAction)
            infoContainer.highlighters.intentions.remove(intentionAction)
          }
        }
        ShowIntentionsPass.markActionInvoked(project, editor, IntentionActionDelegate.unwrap(intentionAction.action))
      }
    }
  }
}