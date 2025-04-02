// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractGoToDeclarationCompletionCommandProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.util.PsiTreeUtil

internal class JavaGoToDeclarationCommandCompletionProvider : AbstractGoToDeclarationCompletionCommandProvider() {
  override fun canNavigateToDeclaration(context: PsiElement): Boolean {
    if (context !is PsiIdentifier) {
      return false
    }
    val javaRef = PsiTreeUtil.getParentOfType(context, PsiJavaCodeReferenceElement::class.java)
    val psiElement = javaRef?.resolve()
    return psiElement != null
  }
}