// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.fix

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
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

  override fun getFamilyName(): String = JvmAnalysisBundle.message("jvm.inspections.rename.quickfix.name")

  override fun getText(): String = JvmAnalysisBundle.message("jvm.inspections.rename.quickfix.text", targetName)

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    val element = previewDescriptor.psiElement.parentOfType<PsiNamedElement>(withSelf = true) ?: return IntentionPreviewInfo.EMPTY
    element.setName(targetName)
    return IntentionPreviewInfo.DIFF
  }

  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    RefactoringFactory.getInstance(project).createRename(startElement, targetName).run()
  }
}