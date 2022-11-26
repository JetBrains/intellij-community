// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework

import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.psi.*
import com.intellij.util.asSafely
import com.intellij.util.containers.ContainerUtil
import com.siyeh.ig.testFrameworks.AssertHint.Companion.createAssertEqualsHint

class JavaTestDiffProvider : JvmTestDiffProvider<PsiMethodCallExpression>() {
  override fun getParamIndex(param: PsiElement): Int? {
    if (param is PsiParameter) {
      return param.parent.asSafely<PsiParameterList>()?.parameters?.indexOf<PsiElement>(param)
    }
    return null
  }

  override fun getFailedCall(file: PsiFile, startOffset: Int, endOffset: Int): PsiMethodCallExpression? {
    val statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset)
    if (statements.isEmpty()) return null
    if (statements.size > 1 && statements.firstOrNull() !is PsiExpressionStatement) return null
    val expression = (statements.firstOrNull() as? PsiExpressionStatement)?.expression
    return if (expression !is PsiMethodCallExpression) null else expression
  }

  override fun getExpected(call: PsiMethodCallExpression, argIndex: Int?): PsiElement? {
    val expr = if (argIndex == null) {
      createAssertEqualsHint(call)?.expected ?: return null
    } else {
      call.argumentList.expressions.getOrNull(argIndex)
    }
    if (expr is PsiLiteralExpression) return expr
    if (expr is PsiPolyadicExpression && ContainerUtil.all(expr.operands) { PsiLiteralExpression::class.java.isInstance(it) }) return expr
    if (expr is PsiReference) return expr.resolve()
    return null
  }
}