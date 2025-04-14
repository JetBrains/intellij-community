// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractGoToImplementationCompletionCommandProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

internal class JavaGoToImplementationCommandCompletionProvider : AbstractGoToImplementationCompletionCommandProvider() {
  override fun canGoToImplementation(element: PsiElement, offset: Int): Boolean {

    val member = PsiTreeUtil.getParentOfType(element, PsiMember::class.java) ?: return false
    if (member is PsiClass) {
      if (!TextRange(member.textRange.startOffset, member.lBrace?.textRange?.startOffset ?: member.textRange.endOffset)
          .contains(offset)
      ) {
        return false
      }
    }
    else if (member is PsiMethod) {
      if (!TextRange(member.textRange.startOffset, member.body?.textRange?.startOffset ?: member.textRange.endOffset)
          .contains(offset)
      ) {
        return false
      }
    }
    else {
      return false
    }
    return true
  }
}
