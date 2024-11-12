// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.Command
import com.intellij.icons.AllIcons
import com.intellij.ide.util.EditSourceUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon

class GoToDeclarationCommand : Command() {
  override val name: String
    get() = "goToDeclaration"

  override val icon: Icon
    get() = AllIcons.Ide.ExternalLinkArrowWhite // Use the appropriate icon

  override fun isApplicable(offset: Int, psiFile: PsiFile): Boolean {
    val context = (getContext(offset, psiFile)) ?: return false
    return canNavigateToDeclaration(context)
  }

  override fun execute(offset: Int, psiFile: PsiFile) {
    val element = getContext(offset, psiFile) ?: return
    if (element !is PsiIdentifier) {
      return
    }
    val javaRef = PsiTreeUtil.getParentOfType(element, PsiJavaCodeReferenceElement::class.java)

    val psiElement = javaRef?.resolve()
    if (psiElement != null) {
      EditSourceUtil.navigateToPsiElement(psiElement)
    }
  }


  private fun canNavigateToDeclaration(context: PsiElement): Boolean {
    if (context !is PsiIdentifier) {
      return false;
    }
    val javaRef = PsiTreeUtil.getParentOfType(context, PsiJavaCodeReferenceElement::class.java)
    val psiElement = javaRef?.resolve()
    return psiElement != null
  }
}