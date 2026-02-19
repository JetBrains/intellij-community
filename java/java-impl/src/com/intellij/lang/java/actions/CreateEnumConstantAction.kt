// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.CodeInsightUtil.positionCursor
import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.startTemplate
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.jvm.actions.CreateEnumConstantActionGroup
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiFile
import com.intellij.psi.util.JavaElementKind
import com.intellij.psi.util.PsiTreeUtil

internal class CreateEnumConstantAction(
  target: PsiClass,
  override val request: CreateFieldRequest
) : CreateFieldActionBase(target, request), HighPriorityAction {

  override fun getActionGroup(): JvmActionGroup = CreateEnumConstantActionGroup

  override fun getText(): String = CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.ENUM_CONSTANT.`object`(), request.fieldName)

  override fun generatePreview(project: Project, editor: Editor, psiFile: PsiFile): IntentionPreviewInfo {
    val constructor = target.constructors.firstOrNull()
    val hasParameters = constructor?.parameters?.isNotEmpty() ?: false
    val text = if (hasParameters) "${request.fieldName}(...)" else request.fieldName
    return IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, "", text)
  }

  override fun invoke(project: Project, file: PsiFile, target: PsiClass) {
    val name = request.fieldName
    val elementFactory = JavaPsiFacade.getElementFactory(project)!!

    // add constant
    var enumConstant = elementFactory.createEnumConstantFromText(name, null)
    enumConstant = target.add(enumConstant) as PsiEnumConstant

    // start template
    val constructor = target.constructors.firstOrNull() ?: return
    val parameters = constructor.parameterList.parameters
    if (parameters.isEmpty()) return

    val paramString = parameters.joinToString(",") { it.name }
    enumConstant = enumConstant.replace(elementFactory.createEnumConstantFromText("$name($paramString)", null)) as PsiEnumConstant

    val builder = TemplateBuilderImpl(enumConstant)
    builder.setScrollToTemplate(request.isStartTemplate)
    val argumentList = enumConstant.argumentList!!
    for (expression in argumentList.expressions) {
      builder.replaceElement(expression, EmptyExpression())
    }
    enumConstant = forcePsiPostprocessAndRestoreElement(enumConstant) ?: return
    val template = builder.buildTemplate()

    val newEditor = positionCursor(project, target.containingFile, enumConstant) ?: return
    val range = enumConstant.textRange
    newEditor.document.deleteString(range.startOffset, range.endOffset)
    startTemplate(newEditor, template, project)
  }
}

internal fun canCreateEnumConstant(targetClass: PsiClass): Boolean {
  if (!targetClass.isEnum) return false

  val lastConstant = targetClass.fields.filterIsInstance<PsiEnumConstant>().lastOrNull()
  return lastConstant == null || !PsiTreeUtil.hasErrorElements(lastConstant)
}
