/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.java.actions

import com.intellij.codeInsight.ExpectedTypeInfo
import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageFix
import com.intellij.codeInsight.daemon.impl.quickfix.JavaCreateFieldFromUsageHelper
import com.intellij.lang.java.request.CreateFieldFromJavaUsageRequest
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import com.intellij.psi.util.PsiUtil

internal class CreateFieldAction(
  targetClass: PsiClass,
  request: CreateFieldRequest,
  private val constantField: Boolean
) : CreateFieldActionBase(targetClass, request) {

  private val myData: FieldData?
    get() {
      val targetClass = myTargetClass.element
      if (targetClass == null || !request.isValid) return null
      return extractRenderData(targetClass, request, constantField)
    }

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    val data = myData ?: return false
    val className = getNameForClass(data.targetClass, false)
    text = if (constantField) {
      message("create.constant.from.usage.full.text", data.fieldName, className)
    }
    else {
      message("create.field.from.usage.full.text", data.fieldName, className)
    }
    return true
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val data = myData ?: return
    val targetClass = data.targetClass

    // render template field
    val renderedField = JavaPsiFacade.getElementFactory(project).createField(data.fieldName, PsiType.INT)

    // clean template modifiers
    renderedField.modifierList?.let { list ->
      list.firstChild?.let {
        list.deleteChildRange(it, list.lastChild)
      }
    }
    // setup actual modifiers
    for (modifier in data.modifiers) {
      PsiUtil.setModifierProperty(renderedField, modifier, true)
    }

    // insert field
    val javaField = JavaCreateFieldFromUsageHelper().insertFieldImpl(targetClass, renderedField, data.anchor)

    // setup template
    CreateFieldFromUsageFix.createFieldFromUsageTemplate(
      targetClass, project, data.fieldType, javaField, data.constant, data.reference
    )
  }
}

private class FieldData(
  val targetClass: PsiClass,
  val fieldName: String,
  val fieldType: Array<ExpectedTypeInfo>,
  val reference: PsiReferenceExpression?,
  val anchor: PsiElement?,
  val modifiers: Collection<String>,
  val constant: Boolean
)

private fun extractRenderData(targetClass: PsiClass, request: CreateFieldRequest, renderConstant: Boolean): FieldData? {
  val fieldName = request.fieldName
  val requestedModifiers = request.modifiers

  val canRender = run {
    val constantRequested = request.constant || targetClass.isInterface || requestedModifiers.containsAll(constantModifiers)
    if (renderConstant) {
      constantRequested || fieldName.toUpperCase() == fieldName
    }
    else {
      !constantRequested
    }
  }
  if (!canRender) return null

  val modifiersToRender = if (renderConstant) {
    if (targetClass.isInterface) {
      // interface fields are public static final implicitly, so modifiers don't have to be rendered
      requestedModifiers - constantModifiers - visibilityModifiers
    }
    else {
      // render static final explicitly
      requestedModifiers + constantModifiers
    }
  }
  else {
    // render as is
    requestedModifiers
  }

  val javaUsage = request as? CreateFieldFromJavaUsageRequest
  return FieldData(
    targetClass,
    fieldName,
    extractExpectedTypes(targetClass.project, request.fieldType).toTypedArray(),
    javaUsage?.reference,
    javaUsage?.anchor,
    modifiersToRender.map(JvmModifier::toPsi),
    renderConstant
  )
}

private val constantModifiers = setOf(
  JvmModifier.STATIC,
  JvmModifier.FINAL
)
