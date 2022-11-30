// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.util.asSafely

/**
 * A quickfix that can call multiple JVM intention actions and bundle them into a single quick fix.
 */
abstract class CompositeIntentionQuickFix: LocalQuickFix {
  override fun startInWriteAction(): Boolean = false

  protected fun generatePreviews(project: Project, previewDescriptor: ProblemDescriptor, element: PsiElement): IntentionPreviewInfo {
    val containingFile = previewDescriptor.startElement.containingFile ?: return IntentionPreviewInfo.EMPTY
    val editor = IntentionPreviewUtils.getPreviewEditor() ?: return IntentionPreviewInfo.EMPTY
    val target = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element)
    getActions(project, previewDescriptor).forEach { factory ->
      val actions = factory(target.element?.nonPreviewElement ?: return@forEach)
      actions.forEach { action ->
        action.generatePreview(project, editor, containingFile)
      }
    }
    return IntentionPreviewInfo.DIFF
  }

  protected fun applyFixes(project: Project, descriptor: ProblemDescriptor, element: PsiElement) {
    val containingFile = descriptor.psiElement.containingFile ?: return
    val target = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element)
    if (!FileModificationService.getInstance().prepareFileForWrite(element.containingFile)) return
    getActions(project, descriptor).forEach { factory ->
      val actions = factory(target.element.asSafely<JvmModifiersOwner>() ?: return@forEach)
      actions.forEach { action ->
        if (action.startInWriteAction()) {
          runWriteAction { action.invoke(project, null, containingFile) }
        } else {
          action.invoke(project, null, containingFile)
        }
      }
    }
  }

  protected abstract fun getActions(project: Project, descriptor: ProblemDescriptor): List<(JvmModifiersOwner) -> List<IntentionAction>>
}