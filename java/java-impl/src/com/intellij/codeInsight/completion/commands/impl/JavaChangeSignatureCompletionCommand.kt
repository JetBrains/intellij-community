// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractChangeSignatureCompletionCommand
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parentOfType

internal class JavaChangeSignatureCompletionCommand : AbstractChangeSignatureCompletionCommand() {
  override fun findChangeSignatureOffset(offset: Int, file: PsiFile): Int? {
    if (offset == 0) return null
    val element = getContext(offset, file) ?: return null
    val callExpression = element.parentOfType<PsiCallExpression>()
    if (callExpression != null && callExpression.resolveMethod()?.isWritable == true) {
      if (callExpression.textRange.endOffset == offset) {
        return callExpression.getArgumentList()?.textRange?.startOffset
      }
      else {
        return offset
      }
    }
    val method = element.parentOfType<PsiMethod>()
    if (method == null) return null
    if ((method.body?.lBrace?.textRange?.startOffset ?: 0) > offset) return offset
    if (method.body?.rBrace?.textRange?.endOffset == offset) return method.parameterList.textRange?.startOffset
    return null
  }
}