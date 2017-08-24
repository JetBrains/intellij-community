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

import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.ExpectedTypeUtil
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.positionCursor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.startTemplate
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.lang.java.actions.Workaround.extractExpectedTypes
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiFile

class CreateEnumConstantAction(targetClass: PsiClass, request: CreateFieldRequest) : CreateFieldActionBase(targetClass, request) {

  private val myData: EnumConstantData?
    get() {
      val targetClass = myTargetClass.element
      if (targetClass == null || !request.isValid) return null
      return extractRenderData(targetClass, request)
    }

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    val data = myData ?: return false
    text = QuickFixBundle.message("create.enum.constant.from.usage.text", data.constantName)
    return true
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val data = myData ?: return
    val name = data.constantName
    val targetClass = data.targetClass
    val elementFactory = JavaPsiFacade.getElementFactory(project)!!

    // add constant
    var enumConstant: PsiEnumConstant
    enumConstant = elementFactory.createEnumConstantFromText(name, null)
    enumConstant = targetClass.add(enumConstant) as PsiEnumConstant

    // start template
    val constructor = targetClass.constructors.firstOrNull() ?: return
    val parameters = constructor.parameterList.parameters
    if (parameters.isEmpty()) return

    val paramString = parameters.joinToString(",") { it.name ?: "" }
    enumConstant = enumConstant.replace(elementFactory.createEnumConstantFromText("$name($paramString)", null)) as PsiEnumConstant

    val builder = TemplateBuilderImpl(enumConstant)
    val argumentList = enumConstant.argumentList!!
    for (expression in argumentList.expressions) {
      builder.replaceElement(expression, EmptyExpression())
    }
    enumConstant = forcePsiPostprocessAndRestoreElement(enumConstant)
    val template = builder.buildTemplate()

    val newEditor = positionCursor(project, targetClass.containingFile, enumConstant) ?: return
    val range = enumConstant.textRange
    newEditor.document.deleteString(range.startOffset, range.endOffset)
    startTemplate(newEditor, template, project)
  }
}

private class EnumConstantData(
  val targetClass: PsiClass,
  val constantName: String
)

private fun extractRenderData(targetClass: PsiClass, request: CreateFieldRequest): EnumConstantData? {
  if (!targetClass.isEnum) return null
  if (!checkExpectedTypes(request.fieldType, targetClass, targetClass.project)) return null
  return EnumConstantData(targetClass, request.fieldName)
}

private fun checkExpectedTypes(types: Any?, targetClass: PsiClass, project: Project): Boolean {
  val typeInfos = extractExpectedTypes(types) ?: return true
  val enumType = JavaPsiFacade.getElementFactory(project).createType(targetClass)
  return typeInfos.any {
    ExpectedTypeUtil.matches(enumType, it)
  }
}
