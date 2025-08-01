// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractTypeInfoCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

internal class JavaTypeInfoCompletionCommandProvider : AbstractTypeInfoCompletionCommandProvider() {
  override fun findElement(offset: Int, psiFile: PsiFile): PsiElement? {
    var context = getCommandContext(offset, psiFile)
    if (context is PsiWhiteSpace) context = context.prevSibling
    val expression = PsiTreeUtil.getParentOfType(context, PsiExpression::class.java, false)
    if (expression != null && expression.type != null) return expression
    return null
  }
}