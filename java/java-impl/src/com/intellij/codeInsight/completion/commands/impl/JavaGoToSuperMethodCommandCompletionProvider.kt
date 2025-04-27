// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractGoToSuperMethodCompletionCommandProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

internal class JavaGoToSuperMethodCommandCompletionProvider : AbstractGoToSuperMethodCompletionCommandProvider() {
  override fun canGoToSuperMethod(element: PsiElement, offset: Int): Boolean {
    val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java) ?: return false
    if (!TextRange(method.textRange.startOffset, method.body?.textRange?.startOffset ?: method.textRange.endOffset)
        .contains(offset)
    ) {
      return false
    }
    val result = method.findSuperMethods().size > 0
    return result
  }
}
