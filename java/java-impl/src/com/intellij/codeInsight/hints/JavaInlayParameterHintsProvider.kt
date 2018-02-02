// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.InferredAnnotationsManager
import com.intellij.codeInsight.completion.CompletionMemory
import com.intellij.codeInsight.hints.HintInfo.MethodInfo
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*

class JavaInlayParameterHintsProvider : InlayParameterHintsProvider {
  
  companion object {
    fun getInstance() = InlayParameterHintsExtension.forLanguage(JavaLanguage.INSTANCE) as JavaInlayParameterHintsProvider
  }
  
  override fun getHintInfo(element: PsiElement): MethodInfo? {
    if (element is PsiCallExpression) {
      val resolvedElement = CompletionMemory.getChosenMethod(element) ?: element.resolveMethodGenerics ().element
      if (resolvedElement is PsiMethod) {
        return getMethodInfo(resolvedElement)
      }
    }
    return null
  }

  override fun getParameterHints(element: PsiElement): List<InlayInfo> {
    if (element is PsiCallExpression) {
      return JavaInlayHintsProvider.hints(element).toList()
    }
    if (element is PsiModifierListOwner && showAnnotations()) {
      val externalAnnotations = ExternalAnnotationsManager.getInstance(element.project).findExternalAnnotations(element)
      val inferredAnnotations = InferredAnnotationsManager.getInstance(element.project).findInferredAnnotations(element)

      return (externalAnnotations.orEmpty().asSequence() + inferredAnnotations.asSequence())
        .mapNotNull { createInlay(it, element) }
        .toList()
    }
    return emptyList()
  }

  private fun createInlay(annotation: PsiAnnotation, element: PsiModifierListOwner): InlayInfo? {
    if (annotation.nameReferenceElement != null && element.modifierList != null) {
      return InlayInfo("@" + annotation.nameReferenceElement?.referenceName + annotation.parameterList.text,
                       element.modifierList!!.textRange.startOffset)
    }
    return null
  }

  private fun showAnnotations(): Boolean {
    val value = Registry.get("java.annotations.show.inline")
    if (value.isBoolean) return value.asBoolean()
    return "internal" == value.asString() && ApplicationManager.getApplication().isInternal
  }

  override fun getInlayPresentation(inlayText: String): String = inlayText

  override fun canShowHintsWhenDisabled(): Boolean {
    return true
  }

  fun getMethodInfo(method: PsiMethod): MethodInfo? {
    val containingClass = method.containingClass ?: return null
    val fullMethodName = StringUtil.getQualifiedName(containingClass.qualifiedName, method.name)

    val paramNames: List<String> = method.parameterList.parameters.map { it.name ?: "" }
    return MethodInfo(fullMethodName, paramNames)
  }

  override fun getDefaultBlackList() = defaultBlackList

  private val defaultBlackList = setOf(
      "(begin*, end*)",
      "(start*, end*)",
      "(first*, last*)",
      "(first*, second*)",
      "(from*, to*)",
      "(min*, max*)",
      "(key, value)",
      "(format, arg*)",
      "(message)",
      "(message, error)",
      
      "*Exception",

      "*.set*(*)",
      "*.add(*)",
      "*.set(*,*)",
      "*.get(*)",
      "*.create(*)",
      "*.getProperty(*)",
      "*.setProperty(*,*)",
      "*.print(*)",
      "*.println(*)",
      "*.append(*)",
      "*.charAt(*)",
      "*.indexOf(*)",
      "*.contains(*)",
      "*.startsWith(*)",
      "*.endsWith(*)",
      "*.equals(*)",
      "*.equal(*)",
      "*.compareTo(*)",
      "*.compare(*,*)",

      "java.lang.Math.*",
      "org.slf4j.Logger.*",
      
      "*.singleton(*)",
      "*.singletonList(*)",
      
      "*.Set.of",
      "*.ImmutableList.of",
      "*.ImmutableMultiset.of",
      "*.ImmutableSortedMultiset.of",
      "*.ImmutableSortedSet.of",
      "*.Arrays.asList"
  )
  
  val isDoNotShowIfMethodNameContainsParameterName = Option("java.method.name.contains.parameter.name", 
                                                            "Do not show if method name contains parameter name", 
                                                            true)
  
  val isShowForParamsWithSameType = Option("java.multiple.params.same.type", 
                                           "Show for non-literals in case of multiple params with the same type", 
                                           false)
  
  val isDoNotShowForBuilderLikeMethods = Option("java.build.like.method",
                                                "Do not show for builder-like methods",
                                                true)


  val ignoreOneCharOneDigitHints = Option("java.simple.sequentially.numbered",
                                          "Do not show for methods with same-named numbered parameters",
                                          true)

  override fun getSupportedOptions(): List<Option> {
    return listOf(
      isDoNotShowIfMethodNameContainsParameterName,
      isShowForParamsWithSameType,
      isDoNotShowForBuilderLikeMethods,
      ignoreOneCharOneDigitHints
    )
  }
}