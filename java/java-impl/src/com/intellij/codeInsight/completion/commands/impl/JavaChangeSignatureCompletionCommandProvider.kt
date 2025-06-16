// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractChangeSignatureCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType

internal class JavaChangeSignatureCompletionCommandProvider : AbstractChangeSignatureCompletionCommandProvider() {
  override fun findChangeSignatureOffset(offset: Int, file: PsiFile): Int? {
    var currentOffset = offset
    if (currentOffset == 0) return null
    var element = getCommandContext(currentOffset, file) ?: return null
    if (element is PsiWhiteSpace) {
      element = PsiTreeUtil.prevVisibleLeaf(element) ?: return null
      currentOffset = element.textRange.startOffset
    }
    val callExpression = element.parentOfType<PsiCallExpression>()
    if (callExpression != null && callExpression.resolveMethod()?.isWritable == true) {
      if (callExpression.textRange.endOffset == currentOffset) {
        return callExpression.getArgumentList()?.textRange?.startOffset
      }
      else {
        return currentOffset
      }
    }
    val method = element.parentOfType<PsiMethod>()
    if (method == null) return null
    if ((method.body?.lBrace?.textRange?.startOffset ?: 0) >= currentOffset ||
        method.body?.rBrace?.textRange?.endOffset == currentOffset) return method.parameterList.textRange?.startOffset
    if (method.body == null && method.parameterList.textRange.endOffset >= currentOffset) return method.parameterList.textRange?.startOffset
    return null
  }
}