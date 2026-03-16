// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractExtractConstantCompletionCommandProvider
import com.intellij.codeInsight.completion.command.commands.AbstractExtractFieldCompletionCommandProvider
import com.intellij.codeInsight.completion.command.commands.AbstractExtractLocalVariableCompletionCommandProvider
import com.intellij.codeInsight.completion.command.commands.AbstractExtractMethodCompletionCommandProvider
import com.intellij.codeInsight.completion.command.commands.AbstractExtractParameterCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiBlockStatement
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiLoopStatement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.findParentOfType

internal class JavaExtractConstantCompletionCommandProvider : AbstractExtractConstantCompletionCommandProvider() {
  override fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int? {
    return findOffsetToExtract(offset, psiFile)
  }
}

internal class JavaExtractFieldCompletionCommandProvider : AbstractExtractFieldCompletionCommandProvider() {
  override fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int? {
    return findOffsetToExtract(offset, psiFile)
  }
}

internal class JavaExtractParameterCompletionCommandProvider : AbstractExtractParameterCompletionCommandProvider() {
  override fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int? {
    return findOffsetToExtract(offset, psiFile)
  }
}

internal class JavaExtractLocalVariableCompletionCommandProvider : AbstractExtractLocalVariableCompletionCommandProvider() {
  override fun findOutermostExpression(offset: Int, psiFile: PsiFile, editor: Editor?): PsiExpression? {
    val expression = findExpressionInsideMethod(offset, psiFile)
    if (
      expression?.findParentOfType<PsiMethod>() != null &&
      (expression.findParentOfType<PsiLocalVariable>() != null || isApplicableCallExpression(expression, offset))
    ) return expression
    return null
  }
}

internal class JavaExtractMethodCompletionCommandProvider : AbstractExtractMethodCompletionCommandProvider(
  actionId = "ExtractMethod",
  presentableName = ActionsBundle.message("action.ExtractMethod.text"),
  previewText = ActionsBundle.message("action.ExtractMethod.description"),
  synonyms = listOf("Extract method", "Introduce method")
) {
  override fun findControlFlowStatement(offset: Int, psiFile: PsiFile): PsiElement? {
    val element = getCommandContext(offset, psiFile) ?: return null
    val elementType = element.elementType
    if (elementType != JavaTokenType.RBRACE && elementType != JavaTokenType.LBRACE) return null

    val parent = element.parent
    if (parent !is PsiCodeBlock) return null

    val blockParent = parent.parent
    if (blockParent !is PsiBlockStatement) return null

    val controlFlowStatement = blockParent.parent
    if (controlFlowStatement is PsiLoopStatement) return controlFlowStatement
    else if (controlFlowStatement is PsiIfStatement) {
      var expression = controlFlowStatement
      while(true) {
        val parent = expression.parent
        if (parent is PsiIfStatement) expression = parent else return expression
      }
    }
    return null
  }

  override fun findOutermostExpression(offset: Int, psiFile: PsiFile, editor: Editor?): PsiElement? {
    val expression = findExpressionInsideMethod(offset, psiFile)
    if (expression?.findParentOfType<PsiMethod>() != null || expression?.findParentOfType<PsiLocalVariable>() != null) return expression
    return expression
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
      if (expression.textRange.endOffset == offset || expression is PsiNewExpression) {
        return expression
      }
      else {
        return null
      }
    }
  }
}

private fun isApplicableCallExpression(expression: PsiExpression?, offset: Int): Boolean {
  return expression is PsiMethodCallExpression
         || expression is PsiNewExpression && (expression.textRange.endOffset != offset || PsiTreeUtil.skipWhitespacesForward(expression) !is PsiErrorElement)
}

private fun findOffsetToExtract(offset: Int, psiFile: PsiFile): Int? {
  var currentOffset = offset
  if (currentOffset == 0) return null
  var element = getCommandContext(offset, psiFile) ?: return null
  if (element is PsiWhiteSpace) {
    element = PsiTreeUtil.skipWhitespacesBackward(element) ?: return null
  }
  currentOffset = element.textRange?.endOffset ?: currentOffset
  val literal = element.findParentOfType<PsiLiteral>(strict = false)
  if (literal != null && literal.textRange.endOffset == currentOffset) {
    return currentOffset
  }
  val localVariable = element.findParentOfType<PsiLocalVariable>() ?: return null
  if (localVariable.textRange.endOffset == currentOffset ||
      localVariable.textRange.endOffset - 1 == currentOffset ||
      localVariable.identifyingElement?.textRange?.endOffset == currentOffset) {
    return currentOffset
  }
  return null
}
