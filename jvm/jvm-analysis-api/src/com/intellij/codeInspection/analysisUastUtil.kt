// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.uast.*

fun ULambdaExpression.getReturnType(): PsiType? {
  val lambdaType = getLambdaType()
  return LambdaUtil.getFunctionalInterfaceReturnType(lambdaType)
}

fun ULambdaExpression.getLambdaType(): PsiType? =
  functionalInterfaceType
   ?: getExpressionType()
   ?: uastParent?.let {
     when (it) {
       is UVariable -> it.type // in Kotlin local functions looks like lambda stored in variable
       is UCallExpression -> it.getParameterForArgument(this)?.type
       else -> null
     }
   }

fun UAnnotated.findAnnotations(vararg fqNames: String) = uAnnotations.filter { ann -> fqNames.contains(ann.qualifiedName) }

/**
 * Gets all attribute values, ignore whether these are written as array initializer.
 *
 * Example:
 * ```
 *  @Z("X")
 * ```
 * Will return "X" as [PsiAnnotationMemberValue]
 *```
 *  @Z("X", 'Y")
 *  @Z(value = ["X", 'Y"])
 *```
 * Will return "X", "Y" as [PsiAnnotationMemberValue]s instead of returning a [PsiArrayInitializerMemberValue] that contains both "X" and
 * "Y".
 * @see PsiAnnotation.flattenedAttributeValues
 */
fun UAnnotation.flattenedAttributeValues(attributeName: String): List<UExpression> {
  fun UExpression.flatten(): List<UExpression> = if (this is UCallExpression) {
    this.valueArguments.flatMap { it.flatten() }
  } else listOf(this)
  val annotationArgument = findDeclaredAttributeValue(attributeName)
  if (annotationArgument == null) return emptyList()
  return annotationArgument.flatten()
}

/**
 * @see UAnnotation.flattenedAttributeValues
 */
fun PsiAnnotation.flattenedAttributeValues(attributeName: String): List<PsiAnnotationMemberValue> {
  fun PsiAnnotationMemberValue.flatten(): List<PsiAnnotationMemberValue> = if (this is PsiArrayInitializerMemberValue) {
    initializers.flatMap { it.flatten() }
  } else listOf(this)

  val annotationArgument = findDeclaredAttributeValue(attributeName)
  if (annotationArgument == null) return emptyList()
  return annotationArgument.flatten()
}

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