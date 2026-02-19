// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jvm.analysis.quickFix

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.RefactoringFactory

class RenameQuickFix(element: PsiElement, private val targetName: String) : LocalQuickFixOnPsiElement(element) {
  override fun startInWriteAction(): Boolean = false

  override fun getFamilyName(): String = CommonQuickFixBundle.message("fix.rename.name")

  override fun getText(): String = CommonQuickFixBundle.message("fix.rename.to.text", targetName)

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    val element = previewDescriptor.psiElement.parentOfType<PsiNamedElement>(withSelf = true) ?: return IntentionPreviewInfo.EMPTY
    element.setName(targetName)
    return IntentionPreviewInfo.DIFF
  }

  override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    RefactoringFactory.getInstance(project).createRename(startElement, targetName).run()
  }
}