// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractTypeHierarchyCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiNameValuePair
import com.intellij.psi.PsiReferenceList
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

internal class JavaTypeHierarchyCompletionCommandProvider : AbstractTypeHierarchyCompletionCommandProvider() {
  override fun findElement(offset: Int, psiFile: PsiFile): PsiElement? {
    var context = getCommandContext(offset, psiFile)
    if (context is PsiWhiteSpace) context = PsiTreeUtil.prevVisibleLeaf(context) ?: return null
    if (context !is PsiIdentifier) return null
    if (context.parent is PsiClass) return context
    if (((context.parent?.parent as? PsiTypeElement)?.type as? PsiClassType)?.resolve() is PsiClass) return context
    if (context.parent?.parent is PsiReferenceList && (context.parent as? PsiJavaCodeReferenceElement)?.resolve() is PsiClass) return context
    if (context.parent is PsiNameValuePair) return context
    return null
  }
}