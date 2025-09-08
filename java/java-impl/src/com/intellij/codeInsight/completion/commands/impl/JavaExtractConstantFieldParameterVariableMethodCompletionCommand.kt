// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.*
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentOfType

internal class JavaExtractConstantCompletionCommandProvider : AbstractExtractConstantCompletionCommandProvider() {
  override fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int? {
    return findOffsetForLocalVariable(offset, psiFile)
  }
}

internal class JavaExtractFieldCompletionCommandProvider : AbstractExtractFieldCompletionCommandProvider() {
  override fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int? {
    return findOffsetForLocalVariable(offset, psiFile)
  }
}

internal class JavaExtractParameterCompletionCommandProvider : AbstractExtractParameterCompletionCommandProvider() {
  override fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int? {
    return findOffsetForLocalVariable(offset, psiFile)
  }
}

internal class JavaExtractLocalVariableCompletionCommandProvider : AbstractExtractLocalVariableCompletionCommandProvider() {
  override fun findOutermostExpression(offset: Int, psiFile: PsiFile, editor: Editor?): PsiExpression? {
    return findExpressionInsideMethod(offset, psiFile)
  }
}

internal class JavaExtractMethodCompletionCommandProvider : AbstractExtractMethodCompletionCommandProvider(
  actionId = "ExtractMethod",
  presentableName = ActionsBundle.message("action.ExtractMethod.text"),
  previewText = ActionsBundle.message("action.ExtractMethod.description"),
  synonyms = listOf("Extract method", "Introduce method")
) {
  override fun findOutermostExpression(offset: Int, psiFile: PsiFile, editor: Editor?): PsiElement? {
    return findExpressionInsideMethod(offset, psiFile)
  }
}

private fun findExpressionInsideMethod(offset: Int, psiFile: PsiFile): PsiExpression? {
  val element = getCommandContext(offset, psiFile) ?: return null
  var expression = element.findParentOfType<PsiExpression>() ?: return null
  while (true) {
    val parent = expression.findParentOfType<PsiExpression>()
    if (parent is PsiExpression && parent.textRange.endOffset == offset) {
      expression = parent
    }
    else {
      if (expression.textRange.endOffset == offset) {
        break
      }
      else {
        return null
      }
    }
  }

  if (expression.findParentOfType<PsiLocalVariable>() == null && expression.findParentOfType<PsiMethod>() == null) return null
  return expression
}

private fun findOffsetForLocalVariable(offset: Int, psiFile: PsiFile): Int? {
  var currentOffset = offset
  if (currentOffset == 0) return null
  var element = getCommandContext(offset, psiFile) ?: return null
  if (element is PsiWhiteSpace) {
    element = PsiTreeUtil.skipWhitespacesBackward(element) ?: return null
  }
  currentOffset = element.textRange?.endOffset ?: currentOffset
  val localVariable = element.findParentOfType<PsiLocalVariable>() ?: return null
  if (localVariable.textRange.endOffset == currentOffset ||
      localVariable.textRange.endOffset - 1 == currentOffset ||
      localVariable.identifyingElement?.textRange?.endOffset == currentOffset) {
    return currentOffset
  }
  return null
}
