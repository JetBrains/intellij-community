// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Represents an abstract completion command specifically designed for navigating to declarations
 * in the code editor.
 */
abstract class AbstractGoToDeclarationCompletionCommand : AbstractActionCompletionCommand("GotoDeclarationOnly",
                                                                         "Go to Declaration",
                                                                         ActionsBundle.message("action.GotoDeclarationOnly.text"),
                                                                         null) {
  final override fun supportsReadOnly(): Boolean  = true

  final override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    return super.isApplicable(offset, psiFile, editor) && hasToShow(offset, psiFile)
  }

  private fun hasToShow(offset: Int, psiFile: PsiFile): Boolean {
    val context = (getContext(offset, psiFile)) ?: return false
    return canNavigateToDeclaration(context)
  }

  /**
   * Determines whether it is possible to navigate to a declaration for a given context element in the code.
   *
   * @param context the PsiElement representing the context in which the navigation is being evaluated
   * @return true if navigation to the declaration is possible, false otherwise
   */
  abstract fun canNavigateToDeclaration(context: PsiElement): Boolean
}