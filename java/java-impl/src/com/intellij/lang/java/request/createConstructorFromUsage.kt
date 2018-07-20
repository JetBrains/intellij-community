// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("CreateConstructorFromUsage")

package com.intellij.lang.java.request

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.lang.jvm.actions.createConstructorActions
import com.intellij.psi.*
import com.intellij.psi.util.parentsOfType
import com.intellij.util.SmartList

fun generateConstructorActions(call: PsiConstructorCall): List<IntentionAction> {
  val targetClass = findTargetClass(call) ?: return emptyList()
  val request = CreateConstructorFromJavaUsageRequest(call, emptyList())
  return createConstructorActions(targetClass, request)
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

fun generateConstructorActions(call: PsiMethodCallExpression): List<IntentionAction> {
  val containingClass = call.parentsOfType<PsiClass>().firstOrNull() ?: return emptyList()
  val result = SmartList<IntentionAction>()

  val superClass = containingClass.superClass
  if (superClass != null) {
    result += createConstructorActions(superClass, CreateConstructorFromCallExpressionRequest(call, "super"))
  }
  result += createConstructorActions(containingClass, CreateConstructorFromCallExpressionRequest(call, "this"))

  return result
}
