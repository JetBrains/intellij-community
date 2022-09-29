// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.jvm.actions.CreateConstantActionGroup
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import com.intellij.psi.util.JavaElementKind

/**
 * This action renders a static final field.
 */
internal class CreateConstantAction(
  target: PsiClass,
  request: CreateFieldRequest
) : CreateFieldActionBase(target, request), HighPriorityAction {

  override fun getActionGroup(): JvmActionGroup = CreateConstantActionGroup

  override fun getText(): String = message("create.element.in.class", JavaElementKind.CONSTANT.`object`(),
                                           request.fieldName, getNameForClass(target, false))

  private fun fieldRenderer(project: Project) = JavaFieldRenderer(project, true, target, request)

  override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
    val field = fieldRenderer(project).renderField()
    return IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, "", field.text)
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    fieldRenderer(project).doRender()
  }
}
