// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractInlineMethodCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.*
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
    if(!element.isWritable) return null
    currentOffset = element.textRange?.endOffset ?: currentOffset
    if (element is PsiIdentifier && element.parent is PsiMethod) return currentOffset
    if (element is PsiJavaToken && element.tokenType == JavaTokenType.RBRACE &&
        element.parent is PsiCodeBlock && element.parent.parent is PsiMethod) return (element.parent.parent as? PsiMethod)?.nameIdentifier?.textRange?.endOffset
    val callExpression = element.findParentOfType<PsiMethodCallExpression>() ?: return null
    val referenceNameElement = callExpression.methodExpression.referenceNameElement
    if (referenceNameElement != null &&
        (callExpression.textRange.endOffset == currentOffset ||
         referenceNameElement.textRange?.endOffset == currentOffset)) {
      return referenceNameElement.textRange?.endOffset
    }
    return null
  }
}