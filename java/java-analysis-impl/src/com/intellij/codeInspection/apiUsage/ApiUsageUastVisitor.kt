// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.apiUsage

import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import com.intellij.uast.UastVisitorAdapter
import com.intellij.util.asSafely
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Non-recursive UAST visitor that detects usages of APIs in source code of UAST-supporting languages
 * and reports them via [ApiUsageProcessor] interface.
 */

@ApiStatus.Experimental
public open class ApiUsageUastVisitor(private val apiUsageProcessor: ApiUsageProcessor) : AbstractUastNonRecursiveVisitor() {

  public companion object {
    @JvmStatic
    public fun createPsiElementVisitor(apiUsageProcessor: ApiUsageProcessor): PsiElementVisitor =
      UastVisitorAdapter(ApiUsageUastVisitor(apiUsageProcessor), true)
  }

  override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
    if (maybeProcessReferenceInsideImportStatement(node)) {
      return true
    }
    if (maybeProcessJavaModuleReference(node)) {
      return true
    }
    if (isMethodReferenceOfCallExpression(node)
        || isNewArrayClassReference(node)
        || isMethodReferenceOfCallableReferenceExpression(node)
        || isSelectorOfQualifiedReference(node)
    ) {
      return true
    }
    if (isSuperOrThisCall(node)) {
      return true
    }
    val resolved = node.resolve()
    if (processClassReferenceInConstructorInvocation(node, resolved)) return true
    if (resolved is PsiModifierListOwner) {
      apiUsageProcessor.processReference(node, resolved, null)
      return true
    }
    return true
  }

  private fun processClassReferenceInConstructorInvocation(node: UReferenceExpression, resolved: PsiElement?): Boolean {
    if (resolved is PsiMethod && isClassReferenceInConstructorInvocation(node)) {
      /*
          Suppose a code:
          ```
             object : SomeClass(42) { }

             or

             new SomeClass(42)
          ```
          with USimpleNameReferenceExpression pointing to `SomeClass`.

          We want ApiUsageProcessor to notice two events: 1) reference to `SomeClass` and 2) reference to `SomeClass(int)` constructor.

          But Kotlin UAST resolves this simple reference to the PSI constructor of the class SomeClass.
          So we resolve it manually to the class because the constructor will be handled separately
          in "visitObjectLiteralExpression" or "visitCallExpression".
        */
      val resolvedClass = resolved.containingClass
      if (resolvedClass != null) {
        apiUsageProcessor.processReference(node, resolvedClass, null)
      }
      return true
    }
    return false
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
    var resolved = node.resolve()
    if (resolved == null) {
      resolved = node.selector.tryResolve()
    }
    if (processClassReferenceInConstructorInvocation(node, resolved)) return true
    if (resolved is PsiModifierListOwner) {
      apiUsageProcessor.processReference(node.selector, resolved, node.receiver)
    }
    return true
  }

  private fun isKotlin(node: UElement): Boolean {
    val sourcePsi = node.sourcePsi ?: return false
    return sourcePsi.language.id.contains("kotlin", true)
  }

  override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
    /*
     * KT-31181: Kotlin UAST: UCallableReferenceExpression.referenceNameElement is always null.
     */
    fun workaroundKotlinGetReferenceNameElement(node: UCallableReferenceExpression): UElement? {
      if (isKotlin(node)) {
        val sourcePsi = node.sourcePsi
        if (sourcePsi != null) {
          val children = sourcePsi.children
          if (children.size == 2) {
            return children[1].toUElement()
          }
        }
      }
      return null
    }

    val resolve = node.resolve()
    if (resolve is PsiModifierListOwner) {
      val sourceNode = node.referenceNameElement ?: workaroundKotlinGetReferenceNameElement(node) ?: node
      apiUsageProcessor.processReference(sourceNode, resolve, node.qualifierExpression)

      //todo support this for other JVM languages
      val javaMethodReference = node.sourcePsi as? PsiMethodReferenceExpression
      if (javaMethodReference != null) {
        //a reference to the functional interface will be added by compiler
        val resolved = PsiUtil.resolveGenericsClassInType(javaMethodReference.functionalInterfaceType).element
        if (resolved != null) {
          apiUsageProcessor.processReference(node, resolved, null)
        }
      }
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

    if (node.kind == UastCallKind.CONSTRUCTOR_CALL) {
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
    val sourceNode = node.methodIdentifier
                     ?: node.classReference?.referenceNameElement
                     ?: node.classReference
                     ?: node.declaration.uastSuperTypes.firstOrNull()
                     ?: node
    if (psiMethod != null) {
      val containingClass = psiMethod.containingClass
      if (psiMethod.isConstructor) {
        if (containingClass != null) {
          apiUsageProcessor.processConstructorInvocation(sourceNode, containingClass, psiMethod, node.declaration)
        }
      }
    }
    else {
      maybeProcessImplicitConstructorInvocationAtSubclassDeclaration(sourceNode, node.declaration)
    }
    return true
  }

  override fun visitNamedExpression(node: UNamedExpression): Boolean {
    //KT-30522: Kotlin does not generate UNamedExpression for annotation's parameters.
    processNamedExpression(node)
    return true
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

  override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
    val explicitClassReference = (node.uastParent as? UCallExpression)?.classReference
    if (explicitClassReference == null) {
      //a reference to the functional interface will be added by compiler
      val resolved = PsiUtil.resolveGenericsClassInType(node.functionalInterfaceType).element
      if (resolved != null) {
        apiUsageProcessor.processLambda(node, resolved)
      }
    }
    return true
  }

  private fun maybeProcessJavaModuleReference(node: UElement): Boolean {
    val sourcePsi = node.sourcePsi
    if (sourcePsi is PsiJavaModuleReferenceElement) {
      val reference = sourcePsi.reference
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
      val parentingQualifier = node.asSafely<USimpleNameReferenceExpression>()?.uastParent.asSafely<UQualifiedReferenceExpression>()
      if (node != parentingQualifier?.selector) {
        val resolved = node.resolve() as? PsiModifierListOwner
        if (resolved != null) {
          apiUsageProcessor.processImportReference(node.referenceNameElement ?: node, resolved)
        }
      }
      return true
    }
    return false
  }

  private fun isInsideImportStatement(node: UReferenceExpression): Boolean {
    var parent = node.uastParent
    while (parent is UReferenceExpression) {
      parent = parent.uastParent
    }
    return parent is UImportStatement
  }

  private fun maybeProcessImplicitConstructorInvocationAtSubclassDeclaration(sourceNode: UElement, subclassDeclaration: UClass) {
    val instantiatedClass = subclassDeclaration.javaPsi.superClass ?: return
    val subclassHasExplicitConstructor = subclassDeclaration.methods.any { it.isConstructor }
    val emptyConstructor = instantiatedClass.constructors.find { it.parameterList.isEmpty }
    if (subclassDeclaration is UAnonymousClass || !subclassHasExplicitConstructor) {
      apiUsageProcessor.processConstructorInvocation(sourceNode, instantiatedClass, emptyConstructor, subclassDeclaration)
    }
  }

  private fun processNamedExpression(node: UNamedExpression) {
    val sourcePsi = node.sourcePsi
    val annotationMethod = sourcePsi?.reference?.resolve() as? PsiAnnotationMethod
    if (annotationMethod != null) {
      val sourceNode = (sourcePsi as? PsiNameValuePair)?.nameIdentifier?.toUElement() ?: node
      apiUsageProcessor.processReference(sourceNode, annotationMethod, null)
    }
  }

  protected fun checkImplicitCallOfSuperEmptyConstructor(constructor: UMethod) {
    val containingUClass = constructor.getContainingUClass() ?: return
    val superClass = containingUClass.javaPsi.superClass ?: return
    val uastBody = constructor.uastBody
    val uastAnchor = constructor.uastAnchor
    if (uastAnchor != null && isImplicitCallOfSuperEmptyConstructorFromSubclassConstructorBody(uastBody)) {
      val emptyConstructor = superClass.constructors.find { it.parameterList.isEmpty }
      apiUsageProcessor.processConstructorInvocation(uastAnchor, superClass, emptyConstructor, null)
    }
  }

  private fun isImplicitCallOfSuperEmptyConstructorFromSubclassConstructorBody(constructorBody: UExpression?): Boolean {
    if (constructorBody == null || constructorBody is UBlockExpression && constructorBody.expressions.isEmpty()) {
      //Empty constructor body => implicit super() call.
      return true
    }
    val firstExpression = (constructorBody as? UBlockExpression)?.expressions?.firstOrNull() ?: constructorBody
    if (firstExpression !is UCallExpression) {
      //First expression is not super() => the super() is implicit.
      return true
    }
    if (firstExpression.valueArgumentCount > 0) {
      //Invocation of non-empty super(args) constructor.
      return false
    }
    val methodName = firstExpression.methodIdentifier?.name ?: firstExpression.methodName
    return methodName != "super" && methodName != "this"
  }

  private fun checkMethodOverriding(node: UMethod) {
    val method = node.javaPsi
    val superMethods = method.findSuperMethods(true)
    for (superMethod in superMethods) {
      apiUsageProcessor.processMethodOverriding(node, superMethod)
    }
  }

  private fun isSelectorOfQualifiedReference(expression: USimpleNameReferenceExpression): Boolean {
    val qualifiedReference = expression.uastParent as? UQualifiedReferenceExpression ?: return false
    return haveSameSourceElement(expression, qualifiedReference.selector)
  }

  private fun isNewArrayClassReference(simpleReference: USimpleNameReferenceExpression): Boolean {
    val callExpression = simpleReference.uastParent as? UCallExpression ?: return false
    return callExpression.kind == UastCallKind.NEW_ARRAY_WITH_DIMENSIONS
  }

  private fun isSuperOrThisCall(simpleReference: UReferenceExpression): Boolean {
    val callExpression = simpleReference.uastParent as? UCallExpression ?: return false
    return callExpression.kind == UastCallKind.CONSTRUCTOR_CALL &&
           (callExpression.methodIdentifier?.name == "super" || callExpression.methodIdentifier?.name == "this")
  }

  private fun isClassReferenceInConstructorInvocation(reference: UReferenceExpression): Boolean {
    if (isSuperOrThisCall(reference)) {
      return false
    }
    val callExpression = reference.uastParent as? UCallExpression ?: return false
    if (callExpression.kind != UastCallKind.CONSTRUCTOR_CALL) {
      return false
    }
    val classReferenceNameElement = callExpression.classReference?.referenceNameElement
    if (classReferenceNameElement != null) {
      return haveSameSourceElement(classReferenceNameElement, reference.referenceNameElement)
    }
    return callExpression.resolve()?.name == reference.resolvedName
  }

  private fun isMethodReferenceOfCallExpression(expression: USimpleNameReferenceExpression): Boolean {
    val callExpression = expression.uastParent as? UCallExpression ?: return false
    if (callExpression.kind != UastCallKind.METHOD_CALL) {
      return false
    }
    val expressionNameElement = expression.referenceNameElement
    val methodIdentifier = callExpression.methodIdentifier
    return methodIdentifier != null && haveSameSourceElement(expressionNameElement, methodIdentifier)
  }

  private fun isMethodReferenceOfCallableReferenceExpression(expression: USimpleNameReferenceExpression): Boolean {
    val callableReferenceExpression = expression.uastParent as? UCallableReferenceExpression ?: return false
    if (haveSameSourceElement(callableReferenceExpression.referenceNameElement, expression)) {
      return true
    }
    return expression.identifier == callableReferenceExpression.callableName
  }

  private fun haveSameSourceElement(element1: UElement?, element2: UElement?): Boolean {
    if (element1 == null || element2 == null) return false
    val sourcePsi1 = element1.sourcePsi
    return sourcePsi1 != null && sourcePsi1 == element2.sourcePsi
  }
}