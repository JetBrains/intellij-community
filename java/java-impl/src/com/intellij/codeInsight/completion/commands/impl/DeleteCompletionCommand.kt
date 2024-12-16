// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.completion.commands.api.ApplicableCompletionCommand
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class DeleteCompletionCommand : ApplicableCompletionCommand(), DumbAware {
  override val name: String
    get() = "Delete"
  override val i18nName: @Nls String
    get() = ActionsBundle.message("action.EditorDelete.text")
  override val icon: Icon?
    get() = null

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val element = getContext(offset, psiFile) ?: return false
    val psiElement = PsiTreeUtil.getParentOfType(element, PsiStatement::class.java, PsiMember::class.java) ?: return false
    return psiElement.textRange.endOffset == offset
  }

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    val element = getContext(offset, psiFile) ?: return
    var psiElement = PsiTreeUtil.getParentOfType(element, PsiStatement::class.java, PsiMember::class.java) ?: return
    var curElement = psiElement
    while (curElement.textRange.endOffset == offset) {
      psiElement = curElement
      curElement = PsiTreeUtil.getParentOfType(curElement, PsiStatement::class.java, PsiMember::class.java) ?: break
    }
    WriteCommandAction.runWriteCommandAction(psiFile.project, null, null, {
      val parent: SmartPsiElementPointer<PsiElement?> = SmartPointerManager.createPointer(psiElement.parent ?: psiFile)
      psiElement.delete()
      PsiDocumentManager.getInstance(psiFile.project).commitDocument(psiFile.fileDocument)
      parent.element?.let {
        ReformatCodeProcessor(psiFile, arrayOf(it.textRange)).run()
      }
    }, psiFile)
  }
}