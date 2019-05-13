// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.request

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateConstructorRequest
import com.intellij.psi.PsiMethodCallExpression

internal class CreateConstructorFromCallExpressionRequest(
  call: PsiMethodCallExpression,
  private val syntheticMethodName: String
) : CreateExecutableFromJavaUsageRequest<PsiMethodCallExpression>(call, listOf(JvmModifier.PUBLIC)), CreateConstructorRequest {

  override fun isValid(): Boolean = super.isValid() && call.methodExpression.referenceName == syntheticMethodName
}
