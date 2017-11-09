// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.request

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.guessExpectedTypes
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromUsageFix.hasErrorsInArgumentList
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.ExpectedTypes
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.util.withPrevious

internal class CreateMethodFromJavaUsageRequest(
  methodCall: PsiMethodCallExpression,
  override val modifiers: Collection<JvmModifier>
) : CreateExecutableFromJavaUsageRequest<PsiMethodCallExpression>(methodCall), CreateMethodRequest {

  override val isValid: Boolean
    get() {
      val call = callPointer.element ?: return false
      call.methodExpression.referenceName ?: return false
      return !hasErrorsInArgumentList(call)
    }

  override val methodName: String get() = call.methodExpression.referenceName!!

  override val returnType: ExpectedTypes get() = guessExpectedTypes(call, call.parent is PsiStatement).map(::ExpectedJavaType)

  fun getAnchor(targetClass: PsiClass): PsiElement? {
    val enclosingMember = call.parentOfType(PsiMethod::class, PsiField::class, PsiClassInitializer::class) ?: return null
    for ((parent, lastParent) in enclosingMember.parents().withPrevious()) {
      if (parent == targetClass) return lastParent
    }
    return null
  }
}
