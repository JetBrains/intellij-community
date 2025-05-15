// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.lang.java.request.CreateFieldFromJavaUsageRequest
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.lang.jvm.actions.JvmGroupIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

internal abstract class CreateFieldActionBase(
  target: PsiClass,
  override val request: CreateFieldRequest
) : CreateMemberAction(target, request), JvmGroupIntentionAction {

  override fun getRenderData(): JvmActionGroup.RenderData = JvmActionGroup.RenderData { request.fieldName }

  private fun fieldRenderer(project: Project) = JavaFieldRenderer(project, isConstant(), target, request)

  override fun isAvailable(project: Project, file: PsiFile, target: PsiClass): Boolean {
    if (!super.isAvailable(project, file, target)) return false
    if (target.findFieldByName(request.fieldName, false) != null) return false;
    return isClassBodyValid(target)
  }

  private fun isClassBodyValid(target: PsiClass): Boolean {
    if (target !is PsiImplicitClass) return true
    if (target.lastChild is PsiErrorElement) return false
    return target.children
      .asSequence()
      .filterIsInstance<PsiMethod>()
      .mapNotNull { it.body }
      .none { it.lastChild is PsiErrorElement }
  }

  override fun invoke(project: Project, file: PsiFile, target: PsiClass) {
    fieldRenderer(project).doRender()
  }

  override fun generatePreview(project: Project, editor: Editor, psiFile: PsiFile): IntentionPreviewInfo {
    val copyClass = PsiTreeUtil.findSameElementInCopy(target, psiFile)
    val javaFieldRenderer = JavaFieldRenderer(project, isConstant(), copyClass, request)
    var field = javaFieldRenderer.renderField()
    field = javaFieldRenderer.insertField(field, PsiTreeUtil.findSameElementInCopy((request as? CreateFieldFromJavaUsageRequest)?.anchor, psiFile))
    javaFieldRenderer.startTemplate(field)
    return IntentionPreviewInfo.DIFF
  }

  internal open fun isConstant(): Boolean = false

  override fun getFamilyName(): String = message("create.field.from.usage.family")
}
