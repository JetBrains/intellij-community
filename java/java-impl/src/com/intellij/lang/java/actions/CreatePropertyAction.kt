// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.generation.GenerateMembersUtil.generateSimpleGetterPrototype
import com.intellij.codeInsight.generation.GenerateMembersUtil.generateSimpleSetterPrototype
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.impl.VariableNode
import com.intellij.lang.java.beans.PropertyKind.*
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.CreatePropertyActionGroup
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import com.intellij.psi.util.PropertyUtilBase.getAccessorName

/**
 * This action renders a property (field + getter + setter) in Java class when getter or a setter is requested.
 */
internal class CreatePropertyAction(target: PsiClass, request: CreateMethodRequest) : CreatePropertyActionBase(target, request) {

  override fun getActionGroup(): JvmActionGroup = CreatePropertyActionGroup

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    if (!super.isAvailable(project, editor, file)) return false
    val (propertyName, propertyKind) = propertyInfo
    val counterPart = when (propertyKind) {
      GETTER, BOOLEAN_GETTER -> SETTER
      SETTER -> {
        val expectedType = request.expectedParameters.single().expectedTypes.singleOrNull()
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

  override fun createRenderer(project: Project): PropertyRenderer = object : PropertyRenderer(project, target, request, propertyInfo) {

    private fun generatePrototypes(): Pair<PsiMethod, PsiMethod> {
      val prototypeField = generatePrototypeField()
      val getter = generateSimpleGetterPrototype(prototypeField)
      val setter = generateSimpleSetterPrototype(prototypeField, target)
      return getter to setter
    }

    private fun insertPrototypes(): Pair<PsiMethod, PsiMethod>? {
      val (getterPrototype, setterPrototype) = generatePrototypes()
      return if (propertyKind == SETTER) {
        // Technology isn't there yet. See related: WEB-26575.
        // We can't recalculate template segments which start before the current segment,
        // so we add the setter before the getter.
        val setter = insertAccessor(setterPrototype) ?: return null
        val getter = insertAccessor(getterPrototype) ?: return null
        getter to setter
      }
      else {
        val getter = insertAccessor(getterPrototype) ?: return null
        val setter = insertAccessor(setterPrototype) ?: return null
        getter to setter
      }
    }

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
    override fun fillTemplate(builder: TemplateBuilderImpl): RangeExpression? {
      val (getter, setter) = insertPrototypes() ?: return null

      val getterData = getter.extractGetterTemplateData()
      val setterData = setter.extractSetterTemplateData()

      val getterTemplate = propertyKind != SETTER
      val inputData = if (getterTemplate) getterData else setterData
      val mirrorData = if (getterTemplate) setterData else getterData

      val typeExpression = builder.setupInput(inputData)
      builder.setupMirror(mirrorData, typeExpression)
      builder.setupSetterParameter(setterData)
      return typeExpression
    }

    private fun TemplateBuilderImpl.setupMirror(mirror: AccessorTemplateData, typeExpression: RangeExpression) {
      replaceElement(mirror.typeElement, typeExpression, false) // copy type text to mirror
      replaceElement(mirror.fieldRef, VariableNode(FIELD_VARIABLE, null), false) // copy field name to mirror
    }
  }
}
