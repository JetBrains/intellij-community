// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class InsertLiveTemplateCompletionCommandProvider : ActionCommandProvider(
  actionId = "InsertLiveTemplate",
  synonyms = listOf("Show live templates", "Live template"),
  presentableName = CodeInsightBundle.message("command.completion.show.live.templates.text"),
  icon = null,
  priority = -150,
  previewText = ActionsBundle.message("action.InsertLiveTemplate.description"),
) {
  override fun supportNewLineCompletion(): Boolean = true

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    return super.isApplicable(offset, psiFile, editor) && isApplicableToProject(offset, psiFile)
  }
}
