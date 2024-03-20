// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.lang.LanguageExtension
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.siyeh.ig.psiutils.CommentTracker
import org.jetbrains.uast.UCallExpression

private val EP_NAME: ExtensionPointName<LoggingStatementNotGuardedByLogCustomFix> = create("com.intellij.codeInspection.customLoggingStatementNotGuardedByLogFix")

/**
 * Provides custom fix for LoggingStatementNotGuardedByLogConditionInspection
 */
interface LoggingStatementNotGuardedByLogCustomFix {
  companion object {
    @JvmField
    val fixProvider = LanguageExtension<LoggingStatementNotGuardedByLogCustomFix>(EP_NAME.name)
  }


  fun fix(call: UCallExpression, textCustomCondition: String)

  fun isAvailable(element: PsiElement): Boolean
}

class JavaLoggingStatementNotGuardedByLogCustomFix : LoggingStatementNotGuardedByLogCustomFix {

  override fun fix(call: UCallExpression, textCustomCondition: String) {
    val methodCallExpression = call.javaPsi
    if (methodCallExpression !is PsiMethodCallExpression) {
      return
    }
    val statement = PsiTreeUtil.getParentOfType(methodCallExpression, PsiStatement::class.java) ?: return
    val logStatements = mutableListOf(statement)
    val methodExpression = methodCallExpression.getMethodExpression()
    var nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement::class.java)
    while (isSameLogMethodCall(nextStatement, methodExpression)) {
      if (nextStatement != null) {
        logStatements.add(nextStatement)
        nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement::class.java)
      }
      else {
        break
      }
    }
    val factory = JavaPsiFacade.getInstance(methodExpression.project).elementFactory
    val qualifier = methodExpression.qualifierExpression
    if (qualifier == null) {
      return
    }
    val ifStatementText = "if (" + qualifier.text + '.' + textCustomCondition + ") {}"
    val ifStatement = factory.createStatementFromText(ifStatementText, statement) as PsiIfStatement
    val blockStatement = (ifStatement.thenBranch as PsiBlockStatement?) ?: return
    val codeBlock = blockStatement.codeBlock
    for (logStatement in logStatements) {
      codeBlock.add(logStatement)
    }
    val firstStatement = logStatements[0]
    val parent = firstStatement.parent
    val codeStyleManager = JavaCodeStyleManager.getInstance(methodCallExpression.project)
    if (parent is PsiIfStatement && parent.elseBranch != null) {
      val newBlockStatement = factory.createStatementFromText("{}", statement) as PsiBlockStatement
      newBlockStatement.codeBlock.add(ifStatement)
      val commentTracker = CommentTracker()
      val result = commentTracker.replace(firstStatement, newBlockStatement)
      codeStyleManager.shortenClassReferences(result)
      return
    }
    val result = parent.addBefore(ifStatement, firstStatement)
    codeStyleManager.shortenClassReferences(result)
    for (logStatement in logStatements) {
      logStatement.delete()
    }
  }

  private fun isSameLogMethodCall(current: PsiStatement?, targetReference: PsiReferenceExpression): Boolean {
    if (current == null) {
      return false
    }
    if (current !is PsiExpressionStatement) {
      return false
    }
    val expression = current.expression
    if (expression !is PsiMethodCallExpression) {
      return false
    }
    val methodExpression = expression.methodExpression
    val referenceName = methodExpression.referenceName
    if (targetReference.referenceName != referenceName) {
      return false
    }
    val qualifier = methodExpression.qualifierExpression
    val qualifierText = qualifier?.text
    return qualifierText != null && qualifierText == targetReference.qualifierExpression?.text
  }


  override fun isAvailable(element: PsiElement): Boolean {
    return element.language == JavaLanguage.INSTANCE
  }
}