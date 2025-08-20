// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractTypeInfoCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.java.syntax.parser.JavaKeywords
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

internal class JavaTypeInfoCompletionCommandProvider : AbstractTypeInfoCompletionCommandProvider() {
  override fun findElement(offset: Int, psiFile: PsiFile): PsiElement? {
    var context = getCommandContext(offset, psiFile)
    if (context is PsiWhiteSpace) context = PsiTreeUtil.prevVisibleLeaf(context) ?: return null
    val expression = PsiTreeUtil.getParentOfType(context, PsiExpression::class.java, false)
    if (expression != null && expression.type != null) return expression
    if (context is PsiIdentifier && context.parent is PsiVariable) return context
    if (context is PsiKeyword && context.text == JavaKeywords.VAR && context.parent is PsiTypeElement) return context
    return null
  }
}