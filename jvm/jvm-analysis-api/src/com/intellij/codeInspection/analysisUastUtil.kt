// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.Language
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.annotationRequest
import com.intellij.lang.jvm.actions.createAddAnnotationActions
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiType
import com.intellij.util.SmartList
import org.jetbrains.uast.*

fun UMethod.createMakeStaticActions(): List<IntentionAction> {
  val jPsi = javaPsi
  val isStatic = isStatic
  val actions = SmartList<IntentionAction>()
  if (!isStatic) actions.addAll(createModifierActions(this, modifierRequest(JvmModifier.STATIC, true)))
  val containingClass = jPsi.containingClass
  if (sourcePsi?.language == Language.findLanguageByID("kotlin") &&
      !jPsi.hasAnnotation("kotlin.jvm.JvmStatic") && ("Companion" == containingClass?.name)
  ) {
    actions.addAll(createAddAnnotationActions(jPsi, annotationRequest("kotlin.jvm.JvmStatic")))
  }
  return actions
}

fun ULambdaExpression.getReturnType(): PsiType? {
  val lambdaType = functionalInterfaceType
                   ?: getExpressionType()
                   ?: uastParent?.let {
                     when (it) {
                       is UVariable -> it.type // in Kotlin local functions looks like lambda stored in variable
                       is UCallExpression -> it.getParameterForArgument(this)?.type
                       else -> null
                     }
                   }
  return LambdaUtil.getFunctionalInterfaceReturnType(lambdaType)
}