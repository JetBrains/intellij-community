// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jvm.analysis.quickFix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager

/**
 * A quickfix that can call multiple JVM intention actions and bundle them into a single quick fix.
 * It's assumed that all the JVM intention actions are well-behaving: they only modify the single PSI file 
 * and don't do anything else like starting template.
 */
abstract class CompositeModCommandQuickFix : PsiUpdateModCommandQuickFix() {
  protected fun applyFixes(project: Project, element: PsiElement, containingFile: PsiFile) {
    val target = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element)
    getActions(project).forEach { factory ->
      val actions = factory(target.element as? JvmModifiersOwner ?: return@forEach)
      performActions(actions, containingFile)
    }
  }

  protected abstract fun getActions(project: Project): List<(JvmModifiersOwner) -> List<IntentionAction>>

  companion object {
    fun performActions(actions: List<IntentionAction>, containingFile: PsiFile) {
      actions.forEach { action ->
        val project = containingFile.project
        val document = containingFile.viewProvider.document
        val manager = PsiDocumentManager.getInstance(project)
        manager.doPostponedOperationsAndUnblockDocument(document)
        action.invoke(project, null, containingFile)
        manager.commitDocument(document)
      }
    }
  }
}