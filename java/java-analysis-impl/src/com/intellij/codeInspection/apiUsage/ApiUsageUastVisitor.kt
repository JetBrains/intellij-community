// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.apiUsage

import com.intellij.psi.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Non-recursive UAST visitor that detects usages of APIs in source code of UAST-supporting languages
 * and reports them via [ApiUsageProcessor] interface.
 */
class ApiUsageUastVisitor(private val apiUsageProcessor: ApiUsageProcessor) : AbstractUastNonRecursiveVisitor() {

  override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
    if (maybeProcessReferenceInsideImportStatement(node)) {
      return true
    }
    if (maybeProcessJavaModuleReference(node)) {
      return true
    }
    if (isMethodReferenceOfCallExpression(node)
        || isSelectorOfQualifiedReference(node)
        || isKotlinConstructorCalleeInObjectDeclarationReference(node)
    ) {
      return true
    }
    val resolved = node.resolve()
    if (resolved is PsiModifierListOwner) {
      apiUsageProcessor.processReference(node, resolved, null)
      return true
    }
    if (resolved == null) {
      /*
       * KT-30522 UAST for Kotlin: reference to annotation parameter resolves to null.
       */
      val psiReferences = node.sourcePsi?.references.orEmpty()
      for (psiReference in psiReferences) {
        val target = psiReference.resolve()?.toUElement()?.javaPsi as? PsiAnnotationMethod
        if (target != null) {
          apiUsageProcessor.processReference(node, target, null)
          return true
        }
      }
    }
    return true
  }

  override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
    if (maybeProcessReferenceInsideImportStatement(node)) {
      return true
    }
    if (node.sourcePsi is PsiMethodCallExpression || node.selector is UCallExpression) {
      //UAST for Java produces UQualifiedReferenceExpression for both PsiMethodCallExpression and PsiReferenceExpression inside it
      //UAST for Kotlin produces UQualifiedReferenceExpression with UCallExpression as selector
      return true
    }
    val uastParent = node.uastParent
    if (uastParent is UCallExpression && uastParent.kind == UastCallKind.CONSTRUCTOR_CALL) {
      //Constructor call will be handled in visitCallExpression().
      return true
    }
    val resolved = node.resolve()
    if (resolved is PsiMember) {
      apiUsageProcessor.processReference(node.selector, resolved, node.receiver)
    }
    return true
  }

  override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
    val resolve = node.resolve()
    if (resolve is PsiMember) {
      val sourceNode = node.referenceNameElement ?: node
      apiUsageProcessor.processReference(sourceNode, resolve, node.qualifierExpression)
    }
    return true
  }

  override fun visitCallExpression(node: UCallExpression): Boolean {
    if (node.sourcePsi is PsiExpressionStatement) {
      //UAST for Java generates UCallExpression for PsiExpressionStatement and PsiMethodCallExpression inside it.
      return true
    }

    val psiMethod = node.resolve()
    val sourceNode = node.methodIdentifier ?: node.classReference?.referenceNameElement ?: node.classReference ?: node
    if (psiMethod != null) {
      val containingClass = psiMethod.containingClass
      if (psiMethod.isConstructor) {
        if (containingClass != null) {
          apiUsageProcessor.processConstructorInvocation(sourceNode, containingClass, psiMethod, null)
        }
      }
      else {
        apiUsageProcessor.processReference(sourceNode, psiMethod, node.receiver)
      }
      return true
    }

    if (node.methodName == "super" && node.valueArgumentCount == 0) {
      //Java does not resolve constructor for subclass constructor's "super()" statement
      // if the superclass has the default constructor, which is not declared in source code and lacks PsiMethod.
      val superClass = node.getContainingUClass()?.javaPsi?.superClass ?: return true
      apiUsageProcessor.processConstructorInvocation(sourceNode, superClass, null, null)
      return true
    }

    val classReference = node.classReference
    if (classReference != null) {
      val resolvedClass = classReference.resolve() as? PsiClass
      if (resolvedClass != null) {
        if (node.kind == UastCallKind.CONSTRUCTOR_CALL) {
          val emptyConstructor = resolvedClass.constructors.find { it.parameterList.isEmpty }
          apiUsageProcessor.processConstructorInvocation(sourceNode, resolvedClass, emptyConstructor, null)
        }
        else {
          apiUsageProcessor.processReference(sourceNode, resolvedClass, node.receiver)
        }
      }
      return true
    }
    return true
  }

  override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean {
    val psiMethod = node.resolve()
    val sourceNode = node.methodIdentifier ?: node.classReference ?: node.declaration.uastSuperTypes.firstOrNull() ?: node
    if (psiMethod != null) {
      val containingClass = psiMethod.containingClass
      if (psiMethod.isConstructor) {
        if (containingClass != null) {
          apiUsageProcessor.processConstructorInvocation(sourceNode, containingClass, psiMethod, node.declaration)
        }
      }
      else {
        apiUsageProcessor.processReference(sourceNode, psiMethod, node.receiver)
      }
    }
    else {
      maybeProcessImplicitConstructorInvocationAtSubclassDeclaration(sourceNode, node.declaration)
    }
    return true
  }

  override fun visitElement(node: UElement): Boolean {
    if (node is UNamedExpression) {
      //IDEA-209279: UAstVisitor lacks a hook for UNamedExpression
      //KT-30522: Kotlin does not generate UNamedExpression for annotation's parameters.
      processNamedExpression(node)
      return true
    }
    return super.visitElement(node)
  }

  override fun visitClass(node: UClass): Boolean {
    val uastAnchor = node.uastAnchor
    if (uastAnchor == null || node is UAnonymousClass || node.javaPsi is PsiTypeParameter) {
      return true
    }
    maybeProcessImplicitConstructorInvocationAtSubclassDeclaration(uastAnchor, node)
    return true
  }

  override fun visitMethod(node: UMethod): Boolean {
    if (node.isConstructor) {
      checkImplicitCallOfSuperEmptyConstructor(node)
    }
    else {
      checkMethodOverriding(node)
    }
    return true
  }

  private fun maybeProcessJavaModuleReference(node: UElement): Boolean {
    val sourcePsi = node.sourcePsi
    val psiParent = sourcePsi?.parent
    if (sourcePsi is PsiIdentifier && psiParent is PsiJavaModuleReferenceElement && sourcePsi == psiParent.lastChild) {
      val reference = psiParent.reference
      val target = reference?.resolve()
      if (target != null) {
        apiUsageProcessor.processJavaModuleReference(reference, target)
      }
      return true
    }
    return false
  }

  private fun maybeProcessReferenceInsideImportStatement(node: UReferenceExpression): Boolean {
    if (isInsideImportStatement(node)) {
      fun isKotlin(node: UElement): Boolean {
        val sourcePsi = node.sourcePsi ?: return false
        return sourcePsi.language.id.contains("kotlin", true)
      }

      if (isKotlin(node)) {
        /*
        UAST for Kotlin 1.3.30 import statements have bugs.

        KT-30546: some references resolve to nulls.
        KT-30957: simple references for members resolve incorrectly to class declaration, not to the member declaration

        Therefore, we have to fallback to base PSI for Kotlin references.
         */
        val resolved = node.sourcePsi?.reference?.resolve()
        val target = (resolved?.toUElement()?.javaPsi ?: resolved) as? PsiModifierListOwner
        if (target != null) {
          apiUsageProcessor.processImportReference(node, target)
        }
      }
      else {
        val resolved = node.resolve() as? PsiModifierListOwner
        if (resolved != null) {
          apiUsageProcessor.processImportReference(node.referenceNameElement ?: node, resolved)
        }
      }
      return true
    }
    return false
  }

  private fun isInsideImportStatement(node: UElement) =
    node.skipParentOfType(true, UQualifiedReferenceExpression::class.java) is UImportStatement

  private fun maybeProcessImplicitConstructorInvocationAtSubclassDeclaration(sourceNode: UElement, subclassDeclaration: UClass) {
    val hasExplicitConstructor = subclassDeclaration.methods.any { it.isConstructor }
    if (!hasExplicitConstructor) {
      val instantiatedClass = subclassDeclaration.javaPsi.superClass ?: return
      val constructor = instantiatedClass.constructors.find { it.parameterList.isEmpty }
      apiUsageProcessor.processConstructorInvocation(sourceNode, instantiatedClass, constructor, subclassDeclaration)
    }
  }

  private fun processNamedExpression(node: UNamedExpression) {
    val sourcePsi = node.sourcePsi
    val annotationMethod = sourcePsi?.reference?.resolve() as? PsiMember
    if (annotationMethod != null) {
      val sourceNode = (sourcePsi as? PsiNameValuePair)?.nameIdentifier?.toUElement() ?: node
      apiUsageProcessor.processReference(sourceNode, annotationMethod, null)
    }
  }

  private fun checkImplicitCallOfSuperEmptyConstructor(constructor: UMethod) {
    val containingUClass = constructor.getContainingUClass() ?: return
    val superClass = containingUClass.javaPsi.superClass ?: return
    val uastBody = constructor.uastBody
    val uastAnchor = constructor.uastAnchor
    if (uastAnchor != null && isImplicitCallOfSuperConstructorFromSubclassConstructorBody(uastBody)) {
      val emptyConstructor = superClass.constructors.find { it.parameterList.isEmpty }
      apiUsageProcessor.processConstructorInvocation(uastAnchor, superClass, emptyConstructor, null)
    }
  }

  private fun isImplicitCallOfSuperConstructorFromSubclassConstructorBody(constructorBody: UExpression?): Boolean {
    if (constructorBody == null || constructorBody is UBlockExpression && constructorBody.expressions.isEmpty()) {
      //Empty constructor body => implicit super() call.
      return true
    }
    val firstExpression = (constructorBody as? UBlockExpression)?.expressions?.firstOrNull() ?: constructorBody
    if (firstExpression !is UCallExpression) {
      //First expression is not super() => the super() is implicit.
      return true
    }
    return firstExpression.methodName != "super"
  }

  private fun checkMethodOverriding(node: UMethod) {
    val method = node.javaPsi
    val superMethods = method.findSuperMethods(true)
    for (superMethod in superMethods) {
      apiUsageProcessor.processMethodOverriding(node, superMethod)
    }
  }

  /**
   * UAST for Kotlin generates UAST tree with UnknownKotlinExpression element, for expression "object : BaseClass() { ... }".
   *
   * ```
   * UObjectLiteralExpression
   *     UnknownKotlinExpression (CONSTRUCTOR_CALLEE)
   *         UTypeReferenceExpression (BaseClass)
   *             USimpleNameReferenceExpression (BaseClass)
   * ```
   *
   * This method checks that [expression] is a simple reference from the super class' constructor invocation.
   * If so, we have to ignore its processing in "visitSimpleNameReferenceExpression" because it will be processed in "visitObjectLiteralExpression".
   */
  private fun isKotlinConstructorCalleeInObjectDeclarationReference(expression: USimpleNameReferenceExpression): Boolean {
    val parent1 = expression.uastParent
    val parent2 = parent1?.uastParent
    val parent3 = parent2?.uastParent
    return parent3 is UObjectLiteralExpression
           && parent1 is UTypeReferenceExpression
           && parent2.asLogString().contains("CONSTRUCTOR_CALLEE")
  }

  private fun isSelectorOfQualifiedReference(expression: USimpleNameReferenceExpression): Boolean {
    val qualifiedReference = expression.uastParent as? UQualifiedReferenceExpression ?: return false
    return haveSameSourceElement(expression, qualifiedReference.selector)
  }

  private fun isMethodReferenceOfCallExpression(expression: USimpleNameReferenceExpression): Boolean {
    val callExpression = expression.uastParent as? UCallExpression ?: return false
    val expressionNameElement = expression.referenceNameElement
    if (expressionNameElement == null && expression.identifier == "super") {
      //UAST for Java returns null for "referenceNameElement" of "super()" statement : IDEA-210418
      return true
    }
    val methodIdentifier = callExpression.methodIdentifier
    val classReference = callExpression.classReference

    return if (methodIdentifier != null) {
      haveSameSourceElement(expressionNameElement, methodIdentifier)
    }
    else {
      haveSameSourceElement(expressionNameElement, classReference?.referenceNameElement ?: classReference)
    }
  }

  private fun haveSameSourceElement(element1: UElement?, element2: UElement?): Boolean {
    if (element1 == null || element2 == null) return false
    val sourcePsi1 = element1.sourcePsi
    return sourcePsi1 != null && sourcePsi1 == element2.sourcePsi
  }
}
