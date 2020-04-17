// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.PrepareFailedException
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase
import com.intellij.refactoring.util.RefactoringUtil
import kotlin.math.exp

class ExtractSelector {

  private fun findSelectedElements(editor: Editor): List<PsiElement> {
    val project = editor.project ?: return emptyList()
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return emptyList()
    val selectionModel: SelectionModel = editor.selectionModel
    if (selectionModel.hasSelection()) {
      val startOffset = selectionModel.selectionStart
      val endOffset = selectionModel.selectionEnd
      var elements: Array<PsiElement>
      val expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset)
      if (expr != null) {
        elements = arrayOf(expr)
      }
      else {
        elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset)
        if (elements.isEmpty()) {
          val expression = IntroduceVariableBase.getSelectedExpression(project, file, startOffset, endOffset)
          if (expression != null && IntroduceVariableBase.getErrorMessage(expression) == null) {
            val originalType = RefactoringUtil.getTypeByExpressionWithExpectedType(expression)
            if (originalType != null) {
              elements = arrayOf(expression)
            }
          }
        }
      }
      return elements.toList()
    }
    return IntroduceVariableBase.collectExpressions(file, editor, editor.caretModel.offset)
  }

  fun suggestElementsToExtract(editor: Editor): List<PsiElement> {
    val selectedElements = findSelectedElements(editor)
    val alignedElements = alignElements(selectedElements)
    if (alignedElements.isEmpty()) throw PrepareFailedException("Fail", selectedElements.first())
    return alignedElements
  }

  private fun alignElements(elements: List<PsiElement>): List<PsiElement> {
    val singleElement = elements.singleOrNull()
    val alignedElements = when {
      elements.size > 1 -> alignStatements(elements)
      singleElement is PsiBlockStatement -> if (singleElement.codeBlock.firstBodyElement != null) listOf(singleElement) else emptyList()
      singleElement is PsiCodeBlock -> alignCodeBlock(singleElement)
      singleElement is PsiExpression -> listOfNotNull(alignExpression(singleElement))
      else -> elements
    }
    return when {
      alignedElements.isEmpty() -> emptyList()
      alignedElements.first() !== elements.first() || alignedElements.last() !== elements.last() -> alignElements(alignedElements)
      else -> alignedElements
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
    return if (filteredStatements.any { it is PsiSwitchLabelStatement }) {
      emptyList()
    } else {
      filteredStatements
    }
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