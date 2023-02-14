// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.lang.jvm.actions.ChangeParametersRequest
import com.intellij.lang.jvm.actions.ExpectedParameter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*

internal class ChangeMethodParameters(
  target: PsiMethod,
  @SafeFieldForPreview override val request: ChangeParametersRequest
) : CreateTargetAction<PsiMethod>(target, request) {
  override fun getText(): String {
    val helper = JvmPsiConversionHelper.getInstance(target.project)
    val parametersString = request.expectedParameters.joinToString(", ", "(", ")") {
      val psiType = helper.convertType(it.expectedTypes.first().theType)
      val name = it.semanticNames.first()
      "${psiType.presentableText} $name"
    }
    val shortenParameterString = StringUtil.shortenTextWithEllipsis(parametersString, 30, 5)
    return QuickFixBundle.message("change.method.parameters.text", shortenParameterString)
  }

  override fun getFamilyName(): String = QuickFixBundle.message("change.method.parameters.family")

  override fun invoke(project: Project, file: PsiFile, target: PsiMethod) {
    val factory = PsiElementFactory.getInstance(project)
    val helper = JvmPsiConversionHelper.getInstance(target.project)

    tailrec fun updateParameters(curParameters: List<PsiParameter>, expParameters: List<ExpectedParameter>) {
      val currentHead = curParameters.firstOrNull()
      val expectedHead = expParameters.firstOrNull()

      if (expectedHead == null) {
        curParameters.forEach(PsiParameter::delete)
        return
      }

      if (expectedHead is ChangeParametersRequest.ExistingParameterWrapper) {
        if (expectedHead.existingParameter == currentHead) {
          return updateParameters(curParameters.subList(1, curParameters.size), expParameters.subList(1, expParameters.size))
        } else throw UnsupportedOperationException("processing of existing params in different order is not implemented yet")
      }

      val name = expectedHead.semanticNames.first()
      val psiType = helper.convertType(expectedHead.expectedTypes.first().theType)
      val newParameter = factory.createParameter(name, psiType)

      // #addAnnotationToModifierList adds annotations to the start of the modifier list instead of its end,
      // reversing the list "nullifies" this behaviour, thus preserving the original annotations order
      for (annotationRequest in expectedHead.expectedAnnotations.reversed()) {
        CreateAnnotationAction.addAnnotationToModifierList(newParameter.modifierList!!, annotationRequest)
      }
      if (currentHead == null) target.parameterList.add(newParameter) else target.parameterList.addBefore(newParameter, currentHead)
      updateParameters(curParameters, expParameters.subList(1, expParameters.size))
    }
    updateParameters(target.parameterList.parameters.toList(), request.expectedParameters)
  }
}
