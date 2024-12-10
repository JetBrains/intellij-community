// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.CompletionCommand
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiStatement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class DeleteCompletionCommand : CompletionCommand() {
  override val name: String
    get() = "Delete"
  override val i18nName: @Nls String
    get() = ActionsBundle.message("action.EditorDelete.text")
  override val icon: Icon?
    get() = null

  override fun isApplicable(offset: Int, psiFile: PsiFile): Boolean {
    val element = getContext(offset, psiFile) ?: return false
    val psiElement = PsiTreeUtil.getParentOfType(element, PsiStatement::class.java, PsiMember::class.java) ?: return false
    return psiElement.textRange.endOffset == offset
  }

  override fun execute(offset: Int, psiFile: PsiFile) {
    val element = getContext(offset, psiFile) ?: return
    val psiElement = PsiTreeUtil.getParentOfType(element, PsiStatement::class.java, PsiMember::class.java) ?: return
    WriteCommandAction.runWriteCommandAction(psiFile.project, null, null, { psiElement.delete() }, psiFile)
  }
}