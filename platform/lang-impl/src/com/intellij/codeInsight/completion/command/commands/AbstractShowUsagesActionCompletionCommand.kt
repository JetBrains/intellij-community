// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.find.actions.ShowUsagesAction
import com.intellij.idea.ActionsBundle
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Abstract base class for defining commands that are intended to execute the "Show Usages" action
 * specifically within the context of code completion.
 */
abstract class AbstractShowUsagesActionCompletionCommand : AbstractActionCompletionCommand(ShowUsagesAction.ID,
                                                                                           "Show usages",
                                                                                           ActionsBundle.message("action.ShowUsages.text"),
                                                                                           null) {
  final override fun supportsReadOnly(): Boolean = true

  final override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val element = getContext(offset, psiFile) ?: return false
    return super.isApplicable(offset, psiFile, editor) && hasToShow(element) &&
           !InjectedLanguageManager.getInstance(psiFile.project).isInjectedFragment(psiFile)
  }

  /**
   * Determines whether a given PSI element should be used to show its usages
   * in the context of the command execution.
   *
   * @param element the PSI element being checked; may be null
   * @return true if the element should be shown, false otherwise
   */
  abstract fun hasToShow(element: PsiElement): Boolean
}