// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("CreateConstructorFromUsage")

package com.intellij.lang.java.request

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.lang.jvm.actions.EP_NAME
import com.intellij.psi.PsiConstructorCall
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiNewExpression

fun generateActions(call: PsiConstructorCall): List<IntentionAction> {
  val targetClass = findTargetClass(call) ?: return emptyList()
  val request = CreateConstructorFromJavaUsageRequest(call, emptyList())
  return EP_NAME.extensions.flatMap { ext ->
    ext.createAddConstructorActions(targetClass, request)
  }
}

private fun findTargetClass(call: PsiConstructorCall): JvmClass? {
  return when (call) {
    is PsiEnumConstant -> findTargetClass(call)
    is PsiNewExpression -> findTargetClass(call)
    else -> null
  }
}

private fun findTargetClass(constant: PsiEnumConstant): JvmClass? {
  val clazz = constant.containingClass ?: return null
  return if (clazz.classKind == JvmClassKind.ENUM) clazz else null
}

private fun findTargetClass(newExpression: PsiNewExpression): JvmClass? {
  val clazz = newExpression.classOrAnonymousClassReference?.resolve() as? JvmClass ?: return null
  return if (clazz.classKind == JvmClassKind.CLASS) clazz else null
}
