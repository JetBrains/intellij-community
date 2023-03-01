// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.asSafely
import com.siyeh.ig.testFrameworks.AssertHint
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter

class JavaTestDiffProvider : JvmTestDiffProvider() {
  override fun failedCall(file: PsiFile, startOffset: Int, endOffset: Int, method: UMethod?): PsiElement? {
    val failedCalls = findCallsInRange(file, startOffset, endOffset)
    if (failedCalls.isEmpty()) return null
    if (failedCalls.size == 1) return failedCalls.first()
    if (method == null) return null
    return failedCalls.firstOrNull { it.resolveMethod()?.isEquivalentTo(method.sourcePsi) == true }
  }

  private fun findCallsInRange(file: PsiFile, startOffset: Int, endOffset: Int): List<PsiMethodCallExpression> {
    val element = file.findElementAt(startOffset)
    val codeBlock = PsiTreeUtil.getParentOfType(element, PsiCodeBlock::class.java)
    return PsiTreeUtil.findChildrenOfAnyType(codeBlock, false, PsiMethodCallExpression::class.java)
      .filter { it.startOffset in startOffset..endOffset }
  }

  override fun getExpected(call: PsiElement, param: UParameter?): PsiElement? {
    if (call !is PsiMethodCallExpression) return null
    val expr = if (param == null) {
      val assertHint = AssertHint.createAssertEqualsHint(call) ?: return null
      if (assertHint.actual.type != PsiType.getJavaLangString(call.manager, call.resolveScope)) return null
      if (assertHint.expected.type != PsiType.getJavaLangString(call.manager, call.resolveScope)) return null
      assertHint.expected
    } else {
      val srcParam = param.sourcePsi?.asSafely<PsiParameter>()
      val paramList = srcParam?.parentOfType<PsiParameterList>()
      val argIndex = paramList?.parameters?.indexOf<PsiElement>(srcParam)
      if (argIndex != null && argIndex != -1) call.argumentList.expressions.getOrNull(argIndex) else null
    }
    if (expr is PsiLiteralExpression) return expr
    // disabled for now
    //if (expr is PsiPolyadicExpression && expr.operands.all { it is PsiLiteralExpression }) return expr
    if (expr is PsiReference) {
      val resolved = expr.resolve()
      if (resolved is PsiParameter) return resolved
      if (resolved is PsiLocalVariable || resolved is PsiField) {
        return resolved.asSafely<PsiVariable>()?.initializer.asSafely<PsiLiteralExpression>()
      }
      return null
    }
    return null
  }
}