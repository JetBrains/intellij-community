// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.find.actions.ShowUsagesAction
import com.intellij.idea.ActionsBundle
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

class ShowUsagesActionCompletionCommand : AbstractActionCompletionCommand(ShowUsagesAction.ID,
                                                                          "Show usages",
                                                                          ActionsBundle.message("action.ShowUsages.text"),
                                                                          null) {
  override fun supportNonWrittenFiles(): Boolean  = true

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    return super.isApplicable(offset, psiFile, editor) && hasToShow(getContext(offset, psiFile)) &&
           !InjectedLanguageManager.getInstance(psiFile.project).isInjectedFragment(psiFile)
  }

  private fun hasToShow(element: PsiElement?): Boolean {
    if (element == null) return false
    val namedIdentifierParent = PsiTreeUtil.getParentOfType(element, PsiNameIdentifierOwner::class.java)
    return namedIdentifierParent?.nameIdentifier == element &&
           (namedIdentifierParent is PsiVariable || namedIdentifierParent is PsiMethod || namedIdentifierParent is PsiMember)
  }
}