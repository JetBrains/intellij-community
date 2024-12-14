// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.CompletionCommand
import com.intellij.codeInsight.completion.commands.core.HighlightInfoLookup
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class IntentionCompletionCommand(
  private val intentionAction: IntentionActionWithTextCaching,
  override val priority: Int?,
  override val icon: Icon?,
  override val highlightInfo: HighlightInfoLookup?,
) : CompletionCommand() {

  override val name: String
    get() = intentionAction.text

  override val i18nName: @Nls String
    get() = intentionAction.text

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    if (editor == null) return
    if (ShowIntentionActionsHandler.availableFor(psiFile, editor, offset, intentionAction.action)) {
      ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, intentionAction.action, name)
    }
  }
}