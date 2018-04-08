// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.ExpectedTypeInfo
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.positionCursor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.ParameterNameExpression
import com.intellij.codeInsight.daemon.impl.quickfix.GuessTypeParameters
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.VariableNode
import com.intellij.lang.java.beans.PropertyKind
import com.intellij.lang.java.request.CreateMethodFromJavaUsageRequest
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.VariableKind

internal abstract class PropertyRenderer(
  private val project: Project,
  private val target: PsiClass,
  private val request: CreateMethodRequest,
  nameKind: Pair<String, PropertyKind>
) {

  private val factory = JavaPsiFacade.getInstance(project).elementFactory
  private val codeStyleManager = JavaCodeStyleManager.getInstance(project)!!
  private val javaUsage = request as? CreateMethodFromJavaUsageRequest
  private val isStatic = JvmModifier.STATIC in request.modifiers
  private val propertyName = nameKind.first
  protected val propertyKind = nameKind.second

  private val suggestedFieldName = run {
    val kind = if (isStatic) VariableKind.STATIC_FIELD else VariableKind.FIELD
    codeStyleManager.propertyNameToVariableName(propertyName, kind)
  }

  protected fun generatePrototypeField(): PsiField {
    val prototypeType = if (propertyKind == PropertyKind.BOOLEAN_GETTER) PsiType.BOOLEAN else PsiType.VOID
    return factory.createField(suggestedFieldName, prototypeType).setStatic(isStatic)
  }

  private val expectedTypes: List<ExpectedTypeInfo> = when (propertyKind) {
    PropertyKind.GETTER -> extractExpectedTypes(project, request.returnType).orObject(target)
    PropertyKind.BOOLEAN_GETTER -> listOf(PsiType.BOOLEAN.toExpectedType())
    PropertyKind.SETTER -> extractExpectedTypes(project, request.expectedParameters.single().expectedTypes).orObject(target)
  }

  private lateinit var targetDocument: Document
  private lateinit var targetEditor: Editor

  private fun navigate(): Boolean {
    val targetFile = target.containingFile ?: return false
    targetDocument = targetFile.viewProvider.document ?: return false
    targetEditor = positionCursor(project, targetFile, target) ?: return false
    return true
  }

  fun doRender() {
    if (!navigate()) return
    val builder = TemplateBuilderImpl(target)
    builder.setGreedyToRight(true)
    val typeExpression = fillTemplate(builder) ?: return
    val template = builder.buildInlineTemplate().apply {
      isToShortenLongNames = true
    }
    val listener = InsertMissingFieldTemplateListener(project, target, typeExpression, isStatic)
    TemplateManager.getInstance(project).startTemplate(targetEditor, template, listener)
  }

  protected abstract fun fillTemplate(builder: TemplateBuilderImpl): RangeExpression?

  protected fun insertAccessor(prototype: PsiMethod): PsiMethod? {
    val method = target.add(prototype) as PsiMethod
    return forcePsiPostprocessAndRestoreElement(method)
  }

  private fun TemplateBuilderImpl.createTemplateContext(): TemplateContext {
    val substitutor = request.targetSubstitutor.toPsiSubstitutor(project)
    val guesser = GuessTypeParameters(project, factory, this, substitutor)
    return TemplateContext(project, factory, target, this, guesser, javaUsage?.context)
  }

  protected fun TemplateBuilderImpl.setupInput(input: AccessorTemplateData): RangeExpression {
    val templateTypeElement = createTemplateContext().setupTypeElement(input.typeElement, expectedTypes)
    val typeExpression = RangeExpression(targetDocument, templateTypeElement.textRange)

    val fieldExpression = FieldExpression(project, target, suggestedFieldName) { typeExpression.text }
    replaceElement(input.fieldRef, FIELD_VARIABLE, fieldExpression, true)

    input.endElement?.let(::setEndVariableAfter)
    return typeExpression
  }

  protected fun TemplateBuilderImpl.setupSetterParameter(data: SetterTemplateData) {
    val suggestedNameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, propertyName, null, null)
    val setterParameterExpression = ParameterNameExpression(suggestedNameInfo.names)
    replaceElement(data.parameterName, SETTER_PARAM_NAME, setterParameterExpression, true)
    replaceElement(data.parameterRef, VariableNode(SETTER_PARAM_NAME, null), false) // copy setter parameter name to mirror
  }
}
