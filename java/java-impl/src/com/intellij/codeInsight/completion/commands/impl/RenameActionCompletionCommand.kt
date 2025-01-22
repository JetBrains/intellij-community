// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.getTargetContext
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement

class RenameActionCompletionCommand : AbstractActionCompletionCommand(IdeActions.ACTION_RENAME,
                                                                      "rename",
                                                                      ActionsBundle.message("action.RenameElement.text"),
                                                                      null) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    val result = getTargetElements(editor, offset)
    return result != null
  }

  private fun getTargetElements(editor: Editor?, targetOffset: Int): PsiElement? {
    if (editor == null) return null
    val context = getTargetContext(targetOffset, editor)
    if (context == null) return null
    if (!context.isWritable || context !is PsiNamedElement) return null
    return context
  }
}