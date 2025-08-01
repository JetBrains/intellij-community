// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractSurroundWithCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.codeInsight.generation.surroundWith.JavaStatementsModCommandSurrounder
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiStatement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.parentOfType

internal class JavaSurroundWithCompletionCommandProvider : AbstractSurroundWithCompletionCommandProvider() {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?, surrounder: Surrounder): Boolean {
    return surrounder is JavaStatementsModCommandSurrounder
  }

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    var currentCommandContext = getCommandContext(offset, psiFile) ?: return false
    var currentOffset = offset
    if (currentCommandContext is PsiWhiteSpace) {
      currentCommandContext = PsiTreeUtil.skipWhitespacesBackward(currentCommandContext) ?: return false
      currentOffset = currentCommandContext.endOffset
    }
    val statement = currentCommandContext.parentOfType<PsiStatement>(withSelf = true) ?: return false
    return statement.textRange.endOffset == currentOffset
  }
}