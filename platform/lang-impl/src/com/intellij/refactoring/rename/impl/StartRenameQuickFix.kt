// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.api.RenameTarget
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * A quick fix, which starts rename on a given target,
 * as if the refactoring was invoked by user via [com.intellij.refactoring.actions.RenameElementAction].
 */
@Internal // TODO candidate for public API
class StartRenameQuickFix(target: RenameTarget) : LocalQuickFix, IntentionAction {

  init {
    ApplicationManager.getApplication().assertReadAccessAllowed()
  }

  private val targetPointer = target.createPointer()

  override fun getFamilyName(): String = CodeInsightBundle.message("rename.element.family")
  override fun getText(): String = familyName
  override fun availableInBatchMode(): Boolean = false
  override fun startInWriteAction(): Boolean = false
  override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement = currentFile

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean {
    return targetPointer.dereference() != null
  }

  override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
    val target = targetPointer.dereference() ?: return
    startRename(project, editor, target)
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    error("Must not be called")
  }
}
