// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.ExpectedTypeUtil
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.positionCursor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.startTemplate
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.lang.jvm.actions.CreateEnumConstantActionGroup
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.ExpectedTypes
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

internal class CreateEnumConstantAction(
  target: PsiClass,
  override val request: CreateFieldRequest
) : CreateFieldActionBase(target, request) {

  override fun getActionGroup(): JvmActionGroup = CreateEnumConstantActionGroup

  override fun getText(): String = QuickFixBundle.message("create.enum.constant.from.usage.text", request.fieldName)

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val name = request.fieldName
    val targetClass = target
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

internal fun canCreateEnumConstant(targetClass: PsiClass, request: CreateFieldRequest): Boolean {
  if (!targetClass.isEnum) return false

  val lastConstant = targetClass.fields.filterIsInstance<PsiEnumConstant>().lastOrNull()
  if (lastConstant != null && PsiTreeUtil.hasErrorElements(lastConstant)) return false

  return checkExpectedTypes(request.fieldType, targetClass, targetClass.project)
}

private fun checkExpectedTypes(types: ExpectedTypes, targetClass: PsiClass, project: Project): Boolean {
  val typeInfos = extractExpectedTypes(project, types)
  if (typeInfos.isEmpty()) return true
  val enumType = JavaPsiFacade.getElementFactory(project).createType(targetClass)
  return typeInfos.any {
    ExpectedTypeUtil.matches(enumType, it)
  }
}
