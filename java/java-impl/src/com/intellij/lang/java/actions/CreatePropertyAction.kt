// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.ExpectedTypeInfo
import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.positionCursor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.ParameterNameExpression
import com.intellij.codeInsight.daemon.impl.quickfix.GuessTypeParameters
import com.intellij.codeInsight.generation.GenerateMembersUtil.generateSimpleGetterPrototype
import com.intellij.codeInsight.generation.GenerateMembersUtil.generateSimpleSetterPrototype
import com.intellij.codeInsight.template.TemplateBuilder
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.VariableNode
import com.intellij.lang.java.beans.PropertyKind
import com.intellij.lang.java.beans.PropertyKind.*
import com.intellij.lang.java.request.CreateMethodFromJavaUsageRequest
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.CreatePropertyActionGroup
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import com.intellij.psi.util.PropertyUtilBase.getAccessorName
import com.intellij.util.component1
import com.intellij.util.component2

/**
 * This action renders a property (field + getter + setter) in Java class when getter or a setter is requested.
 */
internal class CreatePropertyAction(target: PsiClass, request: CreateMethodRequest) : CreatePropertyActionBase(target, request) {

  companion object {
    private const val SETTER_PARAM_NAME = "SETTER_PARAM_NAME"
  }

  override fun getActionGroup(): JvmActionGroup = CreatePropertyActionGroup

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    if (!super.isAvailable(project, editor, file)) return false
    val (propertyName, propertyKind) = propertyInfo
    val counterPart = when (propertyKind) {
      GETTER, BOOLEAN_GETTER -> SETTER
      SETTER -> {
        val expectedType = request.parameters.single().second.singleOrNull()
        if (expectedType != null && PsiType.BOOLEAN == JvmPsiConversionHelper.getInstance(project).convertType(expectedType.theType)) {
          BOOLEAN_GETTER
        }
        else {
          GETTER
        }
      }
    }
    return target.findMethodsByName(getAccessorName(propertyName, counterPart), false).isEmpty()
  }

  override fun getText(): String = message("create.property.from.usage.full.text", propertyInfo.first, getNameForClass(target, false))

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val factory = JavaPsiFacade.getInstance(project).elementFactory
    val codeStyleManager = JavaCodeStyleManager.getInstance(project)!!

    val (propertyName, propertyKind) = propertyInfo
    val target = target

    val isStatic = JvmModifier.STATIC in request.modifiers

    val suggestedFieldName = run {
      val kind = if (isStatic) VariableKind.STATIC_FIELD else VariableKind.FIELD
      codeStyleManager.propertyNameToVariableName(propertyName, kind)
    }

    fun insertPrototypes(): Pair<PsiMethod, PsiMethod> {
      val prototypeType = if (propertyKind == BOOLEAN_GETTER) PsiType.BOOLEAN else PsiType.VOID
      val field = factory.createField(suggestedFieldName, prototypeType).setStatic(isStatic)

      val getterPrototype = generateSimpleGetterPrototype(field)
      val setterPrototype = generateSimpleSetterPrototype(field, target)

      return if (propertyKind == SETTER) {
        // Technology isn't there yet. See related: WEB-26575.
        // We can't recalculate template segments which start before the current segment,
        // so we add the setter before the getter.
        val setter = forcePsiPostprocessAndRestoreElement(target.add(setterPrototype)) as PsiMethod
        val getter = forcePsiPostprocessAndRestoreElement(target.add(getterPrototype)) as PsiMethod
        getter to setter
      }
      else {
        val getter = forcePsiPostprocessAndRestoreElement(target.add(getterPrototype)) as PsiMethod
        val setter = forcePsiPostprocessAndRestoreElement(target.add(setterPrototype)) as PsiMethod
        getter to setter
      }
    }

    val (getter, setter) = insertPrototypes()

    val expectedTypes: List<ExpectedTypeInfo> = when (propertyKind) {
      PropertyKind.GETTER -> extractExpectedTypes(project, request.returnType)
      PropertyKind.BOOLEAN_GETTER -> listOf(PsiType.BOOLEAN.toExpectedType())
      PropertyKind.SETTER -> extractExpectedTypes(project, request.parameters.single().second)
    }

    fun TemplateBuilder.createTemplateContext(): TemplateContext {
      val substitutor = request.targetSubstitutor.toPsiSubstitutor(project)
      val guesserContext = (request as? CreateMethodFromJavaUsageRequest)?.context
      val guesser = GuessTypeParameters(project, factory, this, substitutor)
      return TemplateContext(project, factory, target, this, guesser, guesserContext)
    }

    val targetFile = target.containingFile
    val targetDocument = requireNotNull(targetFile.viewProvider.document)
    val targetEditor = positionCursor(project, targetFile, target) ?: return

    /**
     * Given user want to create a property from a getter reference, such as getFoo.
     * In this case we insert both dummy getter and dummy setter.
     *
     * 1. We add input window on the getter name element, and copy its contents to setter name element.
     * This is done via VariableNode.
     *
     * 2. We add input window on the getter type element, and copy its contents to the setter type element.
     * Problem is that input type element could contain multiple input windows within it,
     * so we have to track the whole type element range to copy it,
     * there is no way to do it via TemplateBuilder, so we track it via RangeExpression.
     *
     * 3. Setter parameter name template is added in any case.
     */
    fun TemplateBuilderImpl.setupTemplate(input: AccessorTemplateData, mirror: AccessorTemplateData): RangeExpression {
      val templateTypeElement = createTemplateContext().setupTypeElement(input.typeElement, expectedTypes)
      val typeExpression = RangeExpression(targetDocument, templateTypeElement.textRange)
      replaceElement(mirror.typeElement, typeExpression, false) // copy type text to mirror

      val fieldExpression = FieldExpression(project, target, suggestedFieldName) { typeExpression.text }
      replaceElement(input.fieldRef, FIELD_VARIABLE, fieldExpression, true)
      replaceElement(mirror.fieldRef, VariableNode(FIELD_VARIABLE, null), false) // copy field name to mirror

      input.endElement?.let(::setEndVariableAfter)
      return typeExpression
    }

    fun TemplateBuilderImpl.setupSetterParameter(data: SetterTemplateData) {
      val suggestedNameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, propertyName, null, null)
      val setterParameterExpression = ParameterNameExpression(suggestedNameInfo.names)
      replaceElement(data.parameterName, SETTER_PARAM_NAME, setterParameterExpression, true)
      replaceElement(data.parameterRef, VariableNode(SETTER_PARAM_NAME, null), false) // copy setter parameter name to mirror
    }

    val getterData = getter.extractGetterTemplateData()
    val setterData = setter.extractSetterTemplateData()
    val builder = TemplateBuilderImpl(target)
    val typeExpression = if (propertyKind == SETTER) {
      builder.setupTemplate(setterData, getterData)
    }
    else {
      builder.setupTemplate(getterData, setterData)
    }
    builder.setupSetterParameter(setterData)

    val template = builder.buildInlineTemplate().apply {
      isToShortenLongNames = true
    }
    val listener = InsertMissingFieldTemplateListener(project, target, typeExpression, isStatic)
    TemplateManager.getInstance(project).startTemplate(targetEditor, template, listener)
  }
}
