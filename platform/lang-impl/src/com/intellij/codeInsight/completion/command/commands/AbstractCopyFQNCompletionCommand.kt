// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.ide.IdeBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Represents an abstract command for copying Fully Qualified Names (FQN) as part of the code completion process.
 * This command provides functionality to determine its applicability based on a specific element within the PSI
 * file and the current editor context.
 */
abstract class AbstractCopyFQNCompletionCommand : AbstractActionCompletionCommand("CopyReference",
                                                                                  "Copy Reference",
                                                                                  IdeBundle.message("copy.reference"),
                                                                                  null,
                                                                                  -150) {
  final override fun supportsReadOnly(): Boolean = true

  final override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    val element = getContext(offset, psiFile)
    if (element == null) return false
    return placeIsApplicable(element, offset)
  }

  /**
   * Determines whether a specific context within a PSI element is applicable for the command execution.
   *
   * @param element The PSI element to evaluate, or null if no context is applicable.
   * @param offset The position in the document where the applicability is being checked.
   * @return true if the place is applicable based on the provided element and offset, false otherwise.
   */
  abstract fun placeIsApplicable(element: PsiElement, offset: Int): Boolean
}
