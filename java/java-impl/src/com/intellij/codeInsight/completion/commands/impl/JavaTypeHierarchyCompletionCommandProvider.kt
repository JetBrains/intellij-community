// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractTypeHierarchyCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.*

internal class JavaTypeHierarchyCompletionCommandProvider : AbstractTypeHierarchyCompletionCommandProvider() {
  override fun findElement(offset: Int, psiFile: PsiFile): PsiElement? {
    var context = getCommandContext(offset, psiFile)
    if (context is PsiWhiteSpace) context = context.prevSibling
    if (context !is PsiIdentifier) return null
    if (context.parent is PsiClass) return context
    if (((context.parent?.parent as? PsiTypeElement)?.type as? PsiClassType)?.resolve() is PsiClass) return context
    if (context.parent?.parent is PsiReferenceList && (context.parent as? PsiJavaCodeReferenceElement)?.resolve() is PsiClass) return context
    if (context.parent is PsiNameValuePair) return context
    return null
  }
}