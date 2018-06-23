// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.ExpectedTypeInfo
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils
import com.intellij.codeInsight.daemon.impl.quickfix.GuessTypeParameters
import com.intellij.codeInsight.template.TemplateBuilder
import com.intellij.lang.jvm.actions.ExpectedParameter
import com.intellij.lang.jvm.actions.ExpectedTypes
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.util.PsiUtil

internal class TemplateContext(
  val project: Project,
  val factory: PsiElementFactory,
  val targetClass: PsiClass,
  val builder: TemplateBuilder,
  val guesser: GuessTypeParameters,
  val guesserContext: PsiElement?
)

internal fun TemplateContext.setupParameters(method: PsiMethod, parameters: List<ExpectedParameter>) {
  if (parameters.isEmpty()) return
  val postprocessReformattingAspect = PostprocessReformattingAspect.getInstance(project)
  val parameterList = method.parameterList
  val isInterface = targetClass.isInterface

  //255 is the maximum number of method parameters
  for (i in 0 until minOf(parameters.size, 255)) {
    val parameterInfo = parameters[i]
    val dummyParameter = factory.createParameter("p$i", PsiType.VOID)
    if (isInterface) {
      PsiUtil.setModifierProperty(dummyParameter, PsiModifier.FINAL, false)
    }
    val parameter = postprocessReformattingAspect.postponeFormattingInside(Computable {
      parameterList.add(dummyParameter)
    }) as PsiParameter
    setupTypeElement(parameter.typeElement, parameterInfo.expectedTypes)
    setupParameterName(parameter, parameterInfo)
  }
}

internal fun TemplateContext.setupTypeElement(typeElement: PsiTypeElement?, types: ExpectedTypes) {
  setupTypeElement(typeElement ?: return, extractExpectedTypes(project, types))
}

@JvmName("setupTypeElementJ")
internal fun TemplateContext.setupTypeElement(typeElement: PsiTypeElement, types: List<ExpectedTypeInfo>): PsiTypeElement {
  return guesser.setupTypeElement(typeElement, types.toTypedArray(), guesserContext, targetClass)
}

internal fun TemplateContext.setupParameterName(parameter: PsiParameter, expectedParameter: ExpectedParameter) {
  val nameIdentifier = parameter.nameIdentifier ?: return
  val codeStyleManager: JavaCodeStyleManager = project.service()
  val argumentType = expectedParameter.expectedTypes.firstOrNull()?.theType as? PsiType
  val names = codeStyleManager.suggestNames(expectedParameter.semanticNames, VariableKind.PARAMETER, argumentType).names
  val expression = CreateFromUsageUtils.ParameterNameExpression(names)
  builder.replaceElement(nameIdentifier, expression)
}
