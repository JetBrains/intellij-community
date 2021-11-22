// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.suggested

import com.intellij.psi.*
import com.intellij.refactoring.suggested.SignaturePresentationBuilder
import com.intellij.refactoring.suggested.SuggestedChangeSignatureData
import com.intellij.refactoring.suggested.SuggestedRefactoringExecution.NewParameterValue
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature
import com.intellij.refactoring.suggested.SuggestedRefactoringUI

object JavaSuggestedRefactoringUI : SuggestedRefactoringUI() {
  override fun createSignaturePresentationBuilder(
    signature: Signature,
    otherSignature: Signature,
    isOldSignature: Boolean
  ): SignaturePresentationBuilder {
    return JavaSignaturePresentationBuilder(signature, otherSignature, isOldSignature)
  }

  override fun extractNewParameterData(data: SuggestedChangeSignatureData): List<NewParameterData> {
    val psiMethod = data.declaration as PsiMethod
    val psiParameters = psiMethod.parameterList.parameters
    val project = psiMethod.project
    val factory = JavaCodeFragmentFactory.getInstance(project)

    fun createCodeFragment(parameterType: PsiType) =
      factory.createExpressionCodeFragment("", psiMethod, parameterType, true)

    return data.newSignature.parameters.zip(psiParameters)
      .filter { (parameter, _) -> data.oldSignature.parameterById(parameter.id) == null }
      .map { (parameter, psiParameter) ->
        val type = psiParameter.type
        NewParameterData(parameter.name, createCodeFragment(type), offerToUseAnyVariable(type))
      }
  }

  private fun offerToUseAnyVariable(parameterType: PsiType): Boolean {
    return when (parameterType) {
      is PsiPrimitiveType -> false
      is PsiClassType -> parameterType.getCanonicalText() != CommonClassNames.JAVA_LANG_OBJECT
      else -> true
    }
  }

  override fun extractValue(fragment: PsiCodeFragment): NewParameterValue.Expression? {
    if ((fragment as PsiExpressionCodeFragment).expression == null) return null
    return NewParameterValue.Expression(fragment.expression!!)
  }
}
