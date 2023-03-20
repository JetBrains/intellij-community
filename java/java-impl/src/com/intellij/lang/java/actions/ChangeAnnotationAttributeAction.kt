// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.lang.jvm.actions.AnnotationAttributeRequest
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

internal class ChangeAnnotationAttributeAction(annotation: PsiAnnotation, val request: AnnotationAttributeRequest) :
  LocalQuickFixAndIntentionActionOnPsiElement(annotation) {

  override fun startInWriteAction(): Boolean = true

  override fun getFamilyName(): String = QuickFixBundle.message("change.annotation.attribute.value.family")

  override fun getText(): String = QuickFixBundle.message("change.annotation.attribute.value.text", request.name)

  override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
    val copy = PsiTreeUtil.findSameElementInCopy(startElement as PsiAnnotation, file)
    invokeImpl(copy, project)
    return IntentionPreviewInfo.DIFF
  }

  override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
    invokeImpl(startElement as PsiAnnotation, project)
  }

  private fun invokeImpl(annotation: PsiAnnotation, project: Project) {
    val factory = PsiElementFactory.getInstance(project)
    val value = CreateAnnotationAction.attributeRequestToValue(request.value, factory, null)
    annotation.setDeclaredAttributeValue(request.name, value)
  }
}