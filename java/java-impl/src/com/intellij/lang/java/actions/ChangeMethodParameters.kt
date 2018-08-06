// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.lang.jvm.actions.ChangeParametersRequest
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
    target.parameterList.parameters.forEach(PsiParameter::delete)

    val factory = PsiElementFactory.SERVICE.getInstance(project)
    val helper = JvmPsiConversionHelper.getInstance(target.project)

    for (expectedParameter in request.expectedParameters) {
      val name = expectedParameter.semanticNames.first()
      val psiType = helper.convertType(expectedParameter.expectedTypes.first().theType)
      target.parameterList.add(factory.createParameter(name, psiType))
    }

  }

}