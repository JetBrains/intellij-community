/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.hints

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod

class JavaInlayParameterHintsProvider : InlayParameterHintsProvider {
  
  companion object {
    fun getInstance() = InlayParameterHintsExtension.forLanguage(JavaLanguage.INSTANCE) as JavaInlayParameterHintsProvider
  }
  
  override fun getMethodInfo(element: PsiElement): MethodInfo? {
    if (element is PsiCallExpression) {
      val resolvedElement = element.resolveMethodGenerics().element
      if (resolvedElement is PsiMethod) {
        return getMethodInfo(resolvedElement)
      }
    }
    return null
  }

  override fun getParameterHints(element: PsiElement): List<InlayInfo> {
    if (element is PsiCallExpression) {
      return JavaInlayHintsProvider.createHints(element).toList()
    }
    return emptyList()
  }

  private fun getMethodInfo(method: PsiMethod): MethodInfo? {
    val containingClass = method.containingClass ?: return null
    val fullMethodName = StringUtil.getQualifiedName(containingClass.qualifiedName, method.name)

    val paramNames: List<String> = method.parameterList.parameters.map { it.name ?: "" }
    return MethodInfo(fullMethodName, paramNames)
  }

  override val defaultBlackList = setOf(
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

      "java.lang.Math.*",
      "org.slf4j.Logger.*"
  )
  
  val isDoNotShowIfMethodNameContainsParameterName = Option("java.method.name.contains.parameter.name", 
                                                            "Do not show if method name contains parameter name", 
                                                            true)
  
  val isShowForParamsWithSameType = Option("java.multiple.params.same.type", 
                                           "Show for non-literals in case of multiple params with the same type", 
                                           false)
  
  override fun getSupportedOptions(): List<Option> {
    return listOf(
      isDoNotShowIfMethodNameContainsParameterName, 
      isShowForParamsWithSameType
    )
  }
}