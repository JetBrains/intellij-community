// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework

import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import com.siyeh.ig.testFrameworks.AssertHint.Companion.createAssertEqualsHint
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter

class JavaTestDiffProvider : JvmTestDiffProvider() {
  override fun isCompiled(file: PsiFile): Boolean {
    return file is PsiCompiledFile
  }

  override fun failedCall(file: PsiFile, startOffset: Int, endOffset: Int, method: UMethod?): PsiElement? {
    val failedCalls = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset)
      .filterIsInstance(PsiExpressionStatement::class.java)
      .map { it.expression }
      .filterIsInstance(PsiMethodCallExpression::class.java)
    if (failedCalls.isEmpty()) return null
    if (failedCalls.size == 1) return failedCalls.first()
    if (method == null) return null
    return failedCalls.firstOrNull { it.resolveMethod()?.isEquivalentTo(method.sourcePsi) == true }
  }

  override fun getExpected(call: PsiElement, param: UParameter?): PsiElement? {
    if (call !is PsiMethodCallExpression) return null
    val expr = if (param == null) {
      createAssertEqualsHint(call)?.expected ?: return null
    } else {
      val srcParam = param.sourcePsi?.asSafely<PsiParameter>()
      val paramList = srcParam?.parentOfType<PsiParameterList>()
      val argIndex = paramList?.parameters?.indexOf<PsiElement>(srcParam)
      if (argIndex != null && argIndex != -1) call.argumentList.expressions.getOrNull(argIndex) else null
    }
    if (expr is PsiLiteralExpression) return expr
    if (expr is PsiPolyadicExpression && expr.operands.all { it is PsiLiteralExpression }) return expr
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