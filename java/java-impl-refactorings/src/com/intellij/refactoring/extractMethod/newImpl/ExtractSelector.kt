// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.IntroduceVariableUtil
import com.intellij.util.CommonJavaRefactoringUtil

class ExtractSelector {

  private fun findElementsInRange(file: PsiFile, range: TextRange): List<PsiElement> {
    val expression = CodeInsightUtil.findExpressionInRange(file, range.startOffset, range.endOffset)
    if (expression != null) return listOf(expression)

    val statements = CodeInsightUtil.findStatementsInRange(file, range.startOffset, range.endOffset)
    if (statements.isNotEmpty()) return statements.toList()

    val subExpression = IntroduceVariableUtil.getSelectedExpression(file.project, file,
                                                                    range.startOffset,
                                                                    range.endOffset)
    if (subExpression != null && IntroduceVariableUtil.getErrorMessage(subExpression) == null) {
      val originalType = CommonJavaRefactoringUtil.getTypeByExpressionWithExpectedType(subExpression)
      if (originalType != null) {
        return listOf(subExpression)
      }
    }

    return emptyList()
  }

  fun suggestElementsToExtract(file: PsiFile, range: TextRange): List<PsiElement> {
    val selectedElements = findElementsInRange(file, range)
    return alignElements(selectedElements)
  }

  private fun alignElements(elements: List<PsiElement>): List<PsiElement> {
    val singleElement = elements.singleOrNull()
    val alignedElements = when {
      elements.size > 1 -> alignStatements(elements)
      singleElement is PsiIfStatement -> listOf(alignIfStatement(singleElement))
      singleElement is PsiBlockStatement -> if (singleElement.codeBlock.firstBodyElement != null) listOf(singleElement) else emptyList()
      singleElement is PsiCodeBlock -> alignCodeBlock(singleElement)
      singleElement is PsiExpression -> listOfNotNull(alignExpression(singleElement))
      singleElement is PsiSwitchLabeledRuleStatement -> listOfNotNull(singleElement.body)
      singleElement is PsiExpressionStatement -> listOf(alignExpressionStatement(singleElement))
      else -> elements
    }
    return when {
      alignedElements.isEmpty() -> emptyList()
      alignedElements.all { it is PsiComment } -> emptyList()
      alignedElements.first() !== elements.first() || alignedElements.last() !== elements.last() -> alignElements(alignedElements)
      else -> alignedElements
    }
  }

  private fun alignExpressionStatement(statement: PsiExpressionStatement): PsiElement {
    val switchRule = statement.parent as? PsiSwitchLabeledRuleStatement
    val switchExpression = PsiTreeUtil.getParentOfType(switchRule, PsiSwitchExpression::class.java)
    if (switchExpression != null) return statement.expression
    return statement
  }

  private fun isControlFlowStatement(statement: PsiStatement?): Boolean {
    return statement is PsiBreakStatement || statement is PsiContinueStatement || statement is PsiReturnStatement || statement is PsiYieldStatement
  }

  private fun alignIfStatement(ifStatement: PsiIfStatement): PsiElement {
    return if (ifStatement.elseBranch == null && isControlFlowStatement(ifStatement.thenBranch)) {
      ifStatement.condition ?: ifStatement
    } else {
      ifStatement
    }
  }

  private tailrec fun alignExpression(expression: PsiExpression?): PsiExpression? {
    return when {
      expression == null -> null
      expression.type is PsiLambdaParameterType -> null
      hasAssignmentInside(expression) -> null
      isInsideAnnotation(expression) -> null
      expression is PsiReturnStatement -> alignExpression(expression.returnValue)
      expression is PsiParenthesizedExpression -> expression.takeIf { expression.expression != null }
      //is PsiAssignmentExpression -> alignExpression(expression.rExpression)
      //expression.parent is PsiParenthesizedExpression -> alignExpression(expression.parent as PsiExpression)
      // PsiUtil.skipParenthesizedExprDown((PsiExpression)elements[0]);
      else -> expression
    }
  }

  private fun hasAssignmentInside(expression: PsiExpression): Boolean {
    return PsiTreeUtil.findChildOfType(expression, PsiAssignmentExpression::class.java, false) != null
  }

  private fun alignStatements(statements: List<PsiElement>): List<PsiElement> {
    val filteredStatements = statements
      .dropWhile { it is PsiSwitchLabelStatement || it is PsiWhiteSpace }
      .dropLastWhile { it is PsiSwitchLabelStatement || it is PsiWhiteSpace }
    if (filteredStatements.any { it is PsiSwitchLabelStatementBase }) return emptyList()
    return filteredStatements
  }

  private fun isInsideAnnotation(expression: PsiExpression): Boolean {
    return PsiTreeUtil.getParentOfType(expression, PsiAnnotation::class.java) != null
  }

  private fun alignCodeBlock(codeBlock: PsiCodeBlock): List<PsiElement> {
    return if (codeBlock.parent is PsiSwitchStatement) {
      listOf(codeBlock.parent)
    } else {
      codeBlock.children.dropWhile { it !== codeBlock.firstBodyElement }.dropLastWhile { it !== codeBlock.lastBodyElement }
    }
  }

  private fun alignStatement(statement: PsiStatement): PsiElement? {
    return when (statement) {
      is PsiExpressionStatement -> alignExpression(statement.expression)
      is PsiDeclarationStatement -> statement.declaredElements.mapNotNull { (it as? PsiLocalVariable)?.initializer }.singleOrNull()
                                    ?: statement
      else -> statement
    }
  }
}