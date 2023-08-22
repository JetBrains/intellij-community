// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.fix

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.lang.jvm.JvmAnnotation
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElementOfType

class RemoveAnnotationQuickFix(annotation: JvmAnnotation) : LocalQuickFix {
  private val annotationPointer = SmartPointerManager.createPointer(annotation as PsiAnnotation)

  override fun getName(): String = JvmAnalysisBundle.message(
    "jvm.inspections.remove.annotation.quickfix.text",
    annotationPointer.element?.qualifiedName?.substringAfterLast(".")
  )

  override fun getFamilyName(): String = JvmAnalysisBundle.message("jvm.inspections.remove.annotation.quickfix.name")

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    val annotation = PsiTreeUtil.findSameElementInCopy(
      annotationPointer.element?.navigationElement, previewDescriptor.psiElement.containingFile
    ).toUElementOfType<UAnnotation>() ?: return IntentionPreviewInfo.EMPTY
    annotation.sourcePsi?.delete()
    return IntentionPreviewInfo.DIFF
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    annotationPointer.element?.delete()
  }
}