// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.apiUsage

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.infos.MethodCandidateInfo

/**
 * Non-recursive PSI visitor that detects usages of APIs and reports them via [ApiUsageDetector] interface.
 * This visitor is mainly designed for Java, but may basically work with any language, including Kotlin.
 * Inheritors should provide at least implementation of [processReference].
 */
abstract class ApiUsageVisitorBase : PsiElementVisitor(), ApiUsageDetector {

  final override fun visitElement(element: PsiElement) {
    super.visitElement(element)
    if (element is PsiLanguageInjectionHost || element is LeafPsiElement) {
      //Better performance.
      return
    }

    when (element) {
      is PsiClass -> visitClass(element)
      is PsiMethod -> visitMethod(element)
      is PsiNewExpression -> visitNewExpression(element)
      else -> processReferences(element)
    }
  }

  private fun processReferences(element: PsiElement) {
    if (shouldProcessReferences(element)) {
      for (reference in element.references) {
        processReference(reference)
      }
    }
  }

  private fun visitClass(aClass: PsiClass) {
    if (aClass is PsiTypeParameter || aClass is PsiAnonymousClass) return
    if (aClass.constructors.isEmpty()) {
      val superClass = aClass.superClass ?: return
      val superConstructors = superClass.constructors
      if (superConstructors.isEmpty() || superConstructors.any { it.parameterList.isEmpty }) {
        processEmptyConstructorOfSuperClassImplicitInvocationAtSubclassDeclaration(aClass, superClass)
      }
    }
  }

  private fun visitMethod(method: PsiMethod) {
    if (method.isConstructor) {
      checkImplicitCallToSuper(method)
    }
    else {
      checkMethodOverriding(method)
    }
  }

  private fun visitNewExpression(expression: PsiNewExpression) {
    var classType = expression.type as? PsiClassType ?: return
    val argumentList = expression.argumentList ?: return
    val classReference = expression.classOrAnonymousClassReference ?: return

    var typeResult = classType.resolveGenerics()
    var aClass = typeResult.element ?: return
    if (aClass is PsiAnonymousClass) {
      classType = aClass.baseClassType
      typeResult = classType.resolveGenerics()
      aClass = typeResult.element ?: return
    }

    if (aClass.constructors.isEmpty()) {
      processDefaultConstructorInvocation(classReference)
    } else {
      val results = JavaPsiFacade
        .getInstance(expression.project)
        .resolveHelper
        .multiResolveConstructor(classType, argumentList, argumentList)
      val result = results.singleOrNull() as? MethodCandidateInfo ?: return
      val constructor = result.element
      processConstructorInvocation(classReference, constructor)
    }
  }

  private fun checkImplicitCallToSuper(constructor: PsiMethod) {
    val superClass = constructor.containingClass?.superClass ?: return
    val statements = constructor.body?.statements ?: return
    if (statements.isEmpty() || !JavaHighlightUtil.isSuperOrThisCall(statements.first(), true, true)) {
      processEmptyConstructorOfSuperClassImplicitInvocationAtSubclassConstructor(superClass, constructor)
    }
  }

  private fun checkMethodOverriding(method: PsiMethod) {
    val superSignatures = method.findSuperMethodSignaturesIncludingStatic(true)
    for (superSignature in superSignatures) {
      val superMethod = superSignature.method
      processMethodOverriding(method, superMethod)
    }
  }

}
