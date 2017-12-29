// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils
import com.intellij.codeInsight.daemon.impl.quickfix.GuessTypeParameters
import com.intellij.codeInsight.template.TemplateBuilder
import com.intellij.lang.jvm.actions.ExpectedParameters
import com.intellij.lang.jvm.actions.ExpectedTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiUtil

internal class TemplateContext(
  val project: Project,
  val factory: PsiElementFactory,
  val targetClass: PsiClass,
  val builder: TemplateBuilder,
  val guesser: GuessTypeParameters,
  val guesserContext: PsiElement?
)

internal fun TemplateContext.setupParameters(method: PsiMethod, parameters: ExpectedParameters) {
  if (parameters.isEmpty()) return
  val codeStyleManager = CodeStyleManager.getInstance(project)!!
  val parameterList = method.parameterList
  val isInterface = targetClass.isInterface

  //255 is the maximum number of method parameters
  for (i in 0 until minOf(parameters.size, 255)) {
    val parameterInfo = parameters[i]
    val names = extractNames(parameterInfo.first) { "p" + i }
    val dummyParameter = factory.createParameter(names.first(), PsiType.INT)
    if (isInterface) {
      PsiUtil.setModifierProperty(dummyParameter, PsiModifier.FINAL, false)
    }
    val parameter = codeStyleManager.performActionWithFormatterDisabled(Computable {
      parameterList.add(dummyParameter)
    }) as PsiParameter
    setupTypeElement(parameter.typeElement, parameterInfo.second)
    setupParameterName(parameter, names)
  }
}

internal fun TemplateContext.setupTypeElement(typeElement: PsiTypeElement?, types: ExpectedTypes) {
  typeElement ?: return
  val expectedTypes = extractExpectedTypes(project, types).toTypedArray()
  guesser.setupTypeElement(typeElement, expectedTypes, guesserContext, targetClass)
}

internal fun TemplateContext.setupParameterName(parameter: PsiParameter, names: Array<out String>) {
  val nameIdentifier = parameter.nameIdentifier ?: return
  val expression = CreateFromUsageUtils.ParameterNameExpression(names)
  builder.replaceElement(nameIdentifier, expression)
}
