// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.util.PsiTreeUtil

class GoToDeclarationCompletionCommand : AbstractActionCompletionCommand("GotoDeclarationOnly",
                                                                         "Go to Declaration",
                                                                         ActionsBundle.message("action.GotoDeclarationOnly.text"),
                                                                         null) {
  override fun supportNonWrittenFiles(): Boolean  = true

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    return super.isApplicable(offset, psiFile, editor) && hasToShow(offset, psiFile)
  }

  private fun hasToShow(offset: Int, psiFile: PsiFile): Boolean {
    val context = (getContext(offset, psiFile)) ?: return false
    return canNavigateToDeclaration(context)
  }

  private fun canNavigateToDeclaration(context: PsiElement): Boolean {
    if (context !is PsiIdentifier) {
      return false
    }
    val javaRef = PsiTreeUtil.getParentOfType(context, PsiJavaCodeReferenceElement::class.java)
    val psiElement = javaRef?.resolve()
    return psiElement != null
  }
}