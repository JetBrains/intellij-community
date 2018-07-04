// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.completion.CompletionMemory
import com.intellij.codeInsight.completion.JavaMethodCallElement
import com.intellij.codeInsight.hints.HintInfo.MethodInfo
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod

class JavaInlayParameterHintsProvider : InlayParameterHintsProvider {
  
  companion object {
    fun getInstance(): JavaInlayParameterHintsProvider = InlayParameterHintsExtension.forLanguage(JavaLanguage.INSTANCE) as JavaInlayParameterHintsProvider
  }
  
  override fun getHintInfo(element: PsiElement): MethodInfo? {
    if (element is PsiCallExpression) {
      val resolvedElement = (if(JavaMethodCallElement.isCompletionMode(element)) CompletionMemory.getChosenMethod(element) else null)
                            ?: element.resolveMethodGenerics().element
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
    return emptyList()
  }

  override fun canShowHintsWhenDisabled(): Boolean {
    return true
  }

  fun getMethodInfo(method: PsiMethod): MethodInfo? {
    val containingClass = method.containingClass ?: return null
    val fullMethodName = StringUtil.getQualifiedName(containingClass.qualifiedName, method.name)

    val paramNames: List<String> = method.parameterList.parameters.map { it.name ?: "" }
    return MethodInfo(fullMethodName, paramNames)
  }

  override fun getDefaultBlackList(): Set<String> = defaultBlackList

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
  
  val isDoNotShowIfMethodNameContainsParameterName: Option = Option("java.method.name.contains.parameter.name",
                                                                    "Do not show if method name contains parameter name",
                                                                    true)
  
  val isShowForParamsWithSameType: Option = Option("java.multiple.params.same.type",
                                                   "Show for non-literals in case of multiple params with the same type",
                                                   false)
  
  val isDoNotShowForBuilderLikeMethods: Option = Option("java.build.like.method",
                                                        "Do not show for builder-like methods",
                                                        true)


  val ignoreOneCharOneDigitHints: Option = Option("java.simple.sequentially.numbered",
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