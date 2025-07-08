// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc

import  com.intellij.codeInsight.javadoc.JavaDocInfoGenerator.DocTagLocator
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator.InheritDocContext
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.javadoc.PsiDocTagValue
import com.intellij.psi.util.MethodSignatureUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object JavaSuperTypeSearchUtil {

  /**
   * Performs automatic supertype search as described in the
   * [JavaDoc Documentation Comment Specification](https://docs.oracle.com/en/java/javase/22/docs/specs/javadoc/doc-comment-spec.html#inheritdoc).
   */
  fun <T> automaticSupertypeSearch(
    psiClass: PsiClass,
    method: PsiMethod,
    target: PsiDocTagValue?,
    locator: DocTagLocator<T>,
  ): InheritDocContext<T>? {
    val resultInDelegate = searchInDelegate(method, target, locator)
    if (resultInDelegate != null) return resultInDelegate

    if (psiClass is PsiAnonymousClass) {
      val baseClass = psiClass.baseClassType.resolve()
      if (baseClass != null) {
        return search(baseClass, method, target, locator, true)
      }
    }

    return search(psiClass, method, target, locator, false)
  }

  private fun <T> searchInDelegate(
    method: PsiMethod,
    target: PsiDocTagValue?,
    locator: DocTagLocator<T>,
  ): InheritDocContext<T>? {
    val delegateMethod = DocumentationDelegateProvider.findDocumentationDelegate(method) as? PsiMethod
    if (delegateMethod != null) {
      val delegateClass = delegateMethod.containingClass
      if (delegateClass != null) {
        val searchInDelegate = search(delegateClass, delegateMethod, target, locator)
        if (searchInDelegate != null) return searchInDelegate
      }
    }
    return null
  }
  
  private fun <T> search(
    psiClass: PsiClass,
    method: PsiMethod,
    target: PsiDocTagValue?,
    locator: DocTagLocator<T>,
    checkClass: Boolean = true,
  ): InheritDocContext<T>? {
    return recursivePhase(psiClass, method, target?.text, locator, checkClass = checkClass)
           ?: finalPhase(psiClass, method, target?.text, locator)
  }

  /**
   * Finds the supertype recursively using the following rules:
   * - first, visit the direct superclass
   * - then, visit superinterfaces in the listed order
   * - skip [java.lang.Object]
   */
  private fun <T> recursivePhase(
    psiClass: PsiClass,
    method: PsiMethod,
    explicitSuper: String?,
    loc: DocTagLocator<T>,
    visited: MutableSet<PsiClass> = mutableSetOf(),
    checkClass: Boolean = true,
  ): InheritDocContext<T>? {
    if (psiClass.qualifiedName == "java.lang.Object") return null
    if (!visited.add(psiClass)) return null

    // Check class
    var target: InheritDocContext<T>? = null
    if (checkClass) target = matchClass(psiClass, method, explicitSuper, loc)
    if (target != null) return target

    // Check superclass
    val superClass = psiClass.superClass
    if (superClass != null) {
      target = recursivePhase(superClass, method, explicitSuper, loc, visited)
      if (target != null) return target
    }

    // Check interfaces
    target = (psiClass.implementsListTypes + psiClass.extendsListTypes)
      .mapNotNull { type -> type.resolve() }
      .firstNotNullOfOrNull { type -> recursivePhase(type, method, explicitSuper, loc, visited) }

    return target
  }

  private fun <T> matchClass(
    psiClass: PsiClass,
    method: PsiMethod,
    explicitSuper: String?,
    loc: DocTagLocator<T>,
  ): InheritDocContext<T>? {
    if (explicitSuper != null && explicitSuper != psiClass.name && explicitSuper != psiClass.qualifiedName) {
      return null
    }

    var matchedMethod = psiClass.findMethodBySignature(method, false)
    // TODO: remove when IDEA-375102 is fixed (findMethodBySignature can fail for methods implemented via default)
    if (matchedMethod == null) matchedMethod = psiClass
      .findMethodsByName(method.name, false)
      .find { m -> MethodSignatureUtil.isSuperMethod(m, method) }
      ?: return null

    val tag: T = loc.find(matchedMethod, JavaDocInfoGenerator.getDocComment(matchedMethod)) ?: return null

    val provider = object : JavaDocInfoGenerator.InheritDocProvider<T> {
      override fun getInheritDoc(target: PsiDocTagValue?): InheritDocContext<T>? {
        return JavaDocInfoGenerator.findInheritDocTag(matchedMethod, loc, target)
      }
      override fun getElement(): PsiClass = psiClass
    }

    return InheritDocContext(tag, provider)
  }

  /**
   * Searches for a match in [java.lang.Object] if the supertype has not been found.
   */
  private fun <T> finalPhase(
    psiClass: PsiClass,
    method: PsiMethod,
    explicitSuper: String?,
    loc: DocTagLocator<T>,
  ): InheritDocContext<T>? {
    val javaLangObject = JavaPsiFacade.getInstance(psiClass.project)
                           .findClass("java.lang.Object", psiClass.resolveScope)
                         ?: return null
    return matchClass(javaLangObject, method, explicitSuper, loc)
  }
}