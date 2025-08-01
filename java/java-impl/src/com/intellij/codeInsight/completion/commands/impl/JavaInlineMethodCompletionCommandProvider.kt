// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractInlineMethodCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentOfType

internal class JavaInlineMethodCompletionCommandProvider : AbstractInlineMethodCompletionCommandProvider() {
  override fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int? {
    var currentOffset = offset
    if (currentOffset == 0) return null
    var element = getCommandContext(offset, psiFile) ?: return null
    if (element is PsiWhiteSpace) {
      element = PsiTreeUtil.skipWhitespacesBackward(element) ?: return null
    }
    currentOffset = element.textRange?.endOffset ?: currentOffset
    val callExpression = element.findParentOfType<PsiMethodCallExpression>() ?: return null
    val referenceNameElement = callExpression.methodExpression.referenceNameElement
    if (referenceNameElement!=null &&
        (callExpression.textRange.endOffset == currentOffset ||
         referenceNameElement.textRange?.endOffset == currentOffset)) {
      return referenceNameElement.textRange?.endOffset
    }
    return null
  }
}