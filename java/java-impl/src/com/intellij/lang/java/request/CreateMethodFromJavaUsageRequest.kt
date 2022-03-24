// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.request

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.guessExpectedTypes
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromUsageFix.hasVoidInArgumentList
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.psi.*
import com.intellij.psi.util.parentOfTypes
import com.intellij.psi.util.parents
import com.intellij.util.containers.withPrevious

internal class CreateMethodFromJavaUsageRequest(
  methodCall: PsiMethodCallExpression,
  modifiers: Collection<JvmModifier>
) : CreateExecutableFromJavaUsageRequest<PsiMethodCallExpression>(methodCall, modifiers), CreateMethodRequest {

  override fun isValid() = super.isValid() && call.let {
    it.methodExpression.referenceName != null && !hasVoidInArgumentList(it)
  }

  override fun getMethodName() = call.methodExpression.referenceName!!

  override fun getReturnType() = guessExpectedTypes(call, call.parent is PsiStatement).map(::ExpectedJavaType)

  fun getAnchor(targetClass: PsiClass): PsiElement? {
    val enclosingMember = call.parentOfTypes(PsiMethod::class, PsiField::class, PsiClassInitializer::class) ?: return null
    for ((parent, lastParent) in enclosingMember.parents(true).withPrevious()) {
      if (parent == targetClass) return lastParent
    }
    return null
  }
}
