// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.lang.jvm.actions.ChangeParametersRequest
import com.intellij.lang.jvm.actions.ExpectedParameter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*

internal class ChangeMethodParameters(target: PsiMethod, override val request: ChangeParametersRequest) : CreateTargetAction<PsiMethod>(
  target, request) {

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

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {

    val factory = PsiElementFactory.getInstance(project)
    val helper = JvmPsiConversionHelper.getInstance(target.project)

    tailrec fun updateParameters(currentParameters: List<PsiParameter>, expectedParameters: List<ExpectedParameter>) {

      val currentHead = currentParameters.firstOrNull()
      val expectedHead = expectedParameters.firstOrNull()

      if (expectedHead == null) {
        currentParameters.forEach(PsiParameter::delete)
        return
      }

      if (expectedHead is ChangeParametersRequest.ExistingParameterWrapper) {
        if (expectedHead.existingParameter == currentHead)
          return updateParameters(currentParameters.subList(1, currentParameters.size),
                                  expectedParameters.subList(1, expectedParameters.size))
        else
          throw UnsupportedOperationException("processing of existing params in different order is not implemented yet")
      }

      val name = expectedHead.semanticNames.first()
      val psiType = helper.convertType(expectedHead.expectedTypes.first().theType)
      val newParameter = factory.createParameter(name, psiType)

      for (annotationRequest in expectedHead.expectedAnnotations) {
        addAnnotationToModifierList(newParameter.modifierList!!, annotationRequest)
      }

      if (currentHead == null)
        target.parameterList.add(newParameter)
      else
        target.parameterList.addBefore(newParameter, currentHead)

      updateParameters(currentParameters, expectedParameters.subList(1, expectedParameters.size))

    }

    updateParameters(target.parameterList.parameters.toList(), request.expectedParameters)
  }

}