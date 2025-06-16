// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.ActionRequest
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

internal abstract class CreateTargetAction<T : PsiElement>(
  target: T,
  @SafeFieldForPreview protected open val request: ActionRequest
) : LocalQuickFixAndIntentionActionOnPsiElement(target) {
  @Suppress("UNCHECKED_CAST")
  protected val target: T get() = startElement as T

  final override fun isAvailable(project: Project, psiFile: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement): Boolean {
    return isAvailable(project, psiFile, target)
  }

  final override fun isAvailable(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
    return isAvailable(project, psiFile, target)
  }

  open fun isAvailable(project: Project, file: PsiFile, target: T): Boolean {
    return request.isValid
  }

  final override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    invoke(project, psiFile, target)
  }

  final override fun invoke(project: Project, psiFile: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
    invoke(project, psiFile, target)
  }

  abstract fun invoke(project: Project, file: PsiFile, target: T)

  override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = target
}

internal abstract class CreateMemberAction(target: PsiClass, request: ActionRequest) : CreateTargetAction<PsiClass>(target, request) {

  open fun getTarget(): JvmClass = target
}
