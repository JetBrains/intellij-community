// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Represents an abstract command to navigate to the implementation(s)
 * of a given element within a code editor during code completion processes.
 */
abstract class AbstractGoToImplementationCompletionCommand : AbstractActionCompletionCommand("GotoImplementation",
                                                                                             "Go to Implementation",
                                                                                             ActionsBundle.message("action.GotoImplementation.text"),
                                                                                             null) {
  final override fun supportsReadOnly(): Boolean = true

  final override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    val element = getContext(offset, psiFile) ?: return false
    return canGoToImplementation(element, offset)
  }

  /**
   * Determines if navigation to the implementation of the specified element is possible.
   *
   * @param element the PSI element for which the implementation is to be checked; can be null
   * @param offset the offset within the element's document, typically representing the caret's position
   * @return true if navigation to the implementation is possible, otherwise false
   */
  abstract fun canGoToImplementation(element: PsiElement, offset: Int): Boolean
}