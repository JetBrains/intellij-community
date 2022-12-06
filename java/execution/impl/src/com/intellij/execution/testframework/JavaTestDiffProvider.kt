// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework

import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.util.asSafely
import com.intellij.util.containers.ContainerUtil
import com.siyeh.ig.testFrameworks.AssertHint.Companion.createAssertEqualsHint
import org.jetbrains.uast.UMethod

class JavaTestDiffProvider : JvmTestDiffProvider<PsiMethodCallExpression>() {
  override fun createActual(project: Project, element: PsiElement, actual: String): PsiElement {
    return PsiElementFactory.getInstance(project).createExpressionFromText("\"$actual\"", element)
  }

  override fun getParamIndex(param: PsiElement): Int? {
    if (param is PsiParameter) {
      return param.parent.asSafely<PsiParameterList>()?.parameters?.indexOf<PsiElement>(param)
    }
    return null
  }

  override fun failedCall(file: PsiFile, startOffset: Int, endOffset: Int, method: UMethod?): PsiMethodCallExpression? {
    val failedCalls = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset)
      .filterIsInstance(PsiExpressionStatement::class.java)
      .map { it.expression }
      .filterIsInstance(PsiMethodCallExpression::class.java)
    if (failedCalls.isEmpty()) return null
    if (failedCalls.size == 1) return failedCalls.first()
    if (method == null) return null
    return failedCalls.firstOrNull { it.resolveMethod() == method.sourcePsi }
  }

  override fun getExpected(call: PsiMethodCallExpression, argIndex: Int?): PsiElement? {
    val expr = if (argIndex == null) {
      createAssertEqualsHint(call)?.expected ?: return null
    } else {
      call.argumentList.expressions.getOrNull(argIndex)
    }
    if (expr is PsiLiteralExpression) return expr
    if (expr is PsiPolyadicExpression && ContainerUtil.all(expr.operands) { PsiLiteralExpression::class.java.isInstance(it) }) return expr
    if (expr is PsiReference) {
      val resolved = expr.resolve()
      if (resolved is PsiVariable) {
        if (resolved is PsiLocalVariable || resolved is PsiField) {
          return resolved.initializer
        }
        return resolved
      }
    }
    return null
  }
}