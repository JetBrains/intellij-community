// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.OldCompletionCommand
import com.intellij.icons.AllIcons
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon

class InlineVariableCompletionCommand : OldCompletionCommand() {
  override val name: String
    get() = "inline"

  override val icon: Icon
    get() = AllIcons.Actions.RefactoringBulb // Use an appropriate icon from IntelliJ's icon set

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (editor == null) return false
    val context = getContext(offset, psiFile)
    if (context !is PsiIdentifier) {
      return false
    }
    val javaRef = PsiTreeUtil.getParentOfType(context, PsiJavaCodeReferenceElement::class.java)

    val psiElement = javaRef?.resolve() ?: return false
    val extensionList = InlineActionHandler.EP_NAME.extensionList
    for (extension in extensionList) {
      try {
        if (extension.canInlineElementInEditor(psiElement, editor)) {
          return true
        }
      }
      catch (_: Exception) {
        continue
      }
    }
    return false
  }

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    val context = getContext(offset, psiFile)
    if (context !is PsiIdentifier) {
      return
    }
    val javaRef = PsiTreeUtil.getParentOfType(context, PsiJavaCodeReferenceElement::class.java)

    val psiElement = javaRef?.resolve() ?: return

    val extensionList = InlineActionHandler.EP_NAME.extensionList
    for (extension in extensionList) {
      if (extension.canInlineElement(psiElement)) {
        extension.inlineElement(psiFile.project, editor, psiElement)
        return
      }
    }
    return
  }

}