// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework

import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.isInjectionHost

class JavaTestDiffProvider : JvmTestDiffProvider() {
  override fun getExpectedElement(expression: UExpression, expected: String): PsiElement? {
    if (expression.isInjectionHost() && expression.asSafely<ULiteralExpression>()?.evaluateString()?.withoutLineEndings() == expected) {
      return expression.sourcePsi
    }
    return null
  }
}