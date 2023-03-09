// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.uast.*

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

fun UAnnotated.findAnnotations(vararg fqNames: String) = uAnnotations.filter { ann -> fqNames.contains(ann.qualifiedName) }

/**
 * Gets all classes in this file, including inner classes.
 */
fun UFile.allClasses() = classes.toTypedArray() + classes.flatMap { it.allInnerClasses().toList() }

fun UClass.allInnerClasses(): Array<UClass> = innerClasses + innerClasses.flatMap { it.allInnerClasses().toList() }

fun UClass.isAnonymousOrLocal(): Boolean = this is UAnonymousClass || isLocal()

fun UClass.isLocal(): Boolean {
  val parent = uastParent
  if (parent is UDeclarationsExpression && parent.uastParent is UBlockExpression) return true
  return if (parent is UClass) parent.isLocal() else false
}

fun PsiType.isInheritorOf(vararg baseClassNames: String) = baseClassNames.any { InheritanceUtil.isInheritor(this, it) }