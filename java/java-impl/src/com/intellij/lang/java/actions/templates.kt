// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.ExpectedTypeInfo
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils
import com.intellij.codeInsight.daemon.impl.quickfix.GuessTypeParameters
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.TemplateBuilder
import com.intellij.lang.jvm.actions.ExpectedParameter
import com.intellij.lang.jvm.actions.ExpectedTypes
import com.intellij.modcommand.ModTemplateBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.PsiTypes
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.util.PsiUtil

/**
 * Adds one template field. It hides the difference between the editor based [TemplateBuilder]
 * and the [ModTemplateBuilder] of a `ModCommandAction`.
 */
internal fun interface TemplateFieldSink {
  fun field(element: PsiElement, expression: Expression)
}

internal class TemplateContext(
  val project: Project,
  val factory: PsiElementFactory,
  val targetClass: PsiClass,
  val fieldSink: TemplateFieldSink,
  val guesser: GuessTypeParameters,
  val guesserContext: PsiElement?
)

internal fun templateContext(
  project: Project,
  factory: PsiElementFactory,
  targetClass: PsiClass,
  builder: TemplateBuilder,
  substitutor: PsiSubstitutor,
  guesserContext: PsiElement?,
): TemplateContext = TemplateContext(
  project, factory, targetClass,
  TemplateFieldSink { element, expression -> builder.replaceElement(element, expression) },
  GuessTypeParameters(project, factory, builder::replaceElement, substitutor),
  guesserContext
)

/**
 * Keeps the template fields, and writes them into a [ModTemplateBuilder] later.
 *
 * A [ModTemplateBuilder] writes the value of a field into the document at once, which leaves the PSI
 * behind. An offset which the caller then reads from the PSI is stale, and
 * `TemplateBuilderImpl.initTemplate` rejects the resulting range. So the caller makes every PSI
 * change first, and calls [flushTo] at the end.
 */
internal class RecordedTemplateFields(private val project: Project) : TemplateFieldSink {
  private val fields = ArrayList<Pair<SmartPsiElementPointer<PsiElement>, Expression>>()

  override fun field(element: PsiElement, expression: Expression) {
    fields += SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element) to expression
  }

  fun flushTo(builder: ModTemplateBuilder) {
    for ((pointer, expression) in fields) {
      val element = pointer.element ?: continue
      builder.field(element, expression)
    }
  }

  fun isEmpty(): Boolean = fields.isEmpty()
}

internal fun templateContext(
  project: Project,
  factory: PsiElementFactory,
  targetClass: PsiClass,
  fields: RecordedTemplateFields,
  substitutor: PsiSubstitutor,
  guesserContext: PsiElement?,
): TemplateContext = TemplateContext(
  project, factory, targetClass,
  fields,
  GuessTypeParameters(project, factory, fields::field, substitutor),
  guesserContext
)

internal fun TemplateContext.setupParameters(method: PsiMethod, parameters: List<ExpectedParameter>) {
  if (parameters.isEmpty()) return
  val postprocessReformattingAspect = PostprocessReformattingAspect.getInstance(project)
  val parameterList = method.parameterList
  val notFinal = targetClass.isInterface || method.hasModifierProperty(PsiModifier.ABSTRACT)

  //255 is the maximum number of method parameters
  for (i in 0 until minOf(parameters.size, 255)) {
    val parameterInfo = parameters[i]
    val dummyParameter = factory.createParameter("p$i", PsiTypes.voidType())
    if (notFinal) {
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
  setupTypeElement(typeElement ?: return, extractExpectedTypes(project, types, typeElement))
}

@JvmName("setupTypeElementJ")
internal fun TemplateContext.setupTypeElement(typeElement: PsiTypeElement, types: List<ExpectedTypeInfo>): PsiTypeElement {
  return guesser.setupTypeElement(typeElement, types.toTypedArray(), guesserContext, targetClass)
}

internal fun TemplateContext.setupParameterName(parameter: PsiParameter, expectedParameter: ExpectedParameter) {
  val nameIdentifier = parameter.nameIdentifier ?: return
  val codeStyleManager = JavaCodeStyleManager.getInstance(project)
  val argumentType = expectedParameter.expectedTypes.firstOrNull()?.theType as? PsiType
  val names = codeStyleManager.suggestNames(expectedParameter.semanticNames, VariableKind.PARAMETER, argumentType).names
  val expression = CreateFromUsageUtils.ParameterNameExpression(names)
  fieldSink.field(nameIdentifier, expression)
}
