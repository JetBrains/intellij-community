// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractQuickDocumentationCompletionCommand
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiReferenceList
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

internal class JavaQuickDocumentationCompletionCommand : AbstractQuickDocumentationCompletionCommand() {
  override fun findElement(offset: Int, psiFile: PsiFile): PsiElement? {
    var context = getCommandContext(offset, psiFile) ?: return null
    if (context is PsiWhiteSpace) context = PsiTreeUtil.prevVisibleLeaf(context) ?: return null
    if (context !is PsiIdentifier) return null
    if (context.parent is PsiMember) return context
    //example:
    // String.<caret> a = "1";
    if (((context.parent?.parent as? PsiTypeElement)?.type as? PsiClassType)?.resolve() is PsiMember) return context
    //example:
    // void a() throws Exception.<caret>{}
    if (context.parent?.parent is PsiReferenceList && (context.parent as? PsiJavaCodeReferenceElement)?.resolve() is PsiMember) return context
    val value = PsiTreeUtil.getParentOfType(context, PsiJavaCodeReferenceElement::class.java, false) ?: return null
    val resolved = value.resolve()
    if (resolved is PsiMember || resolved is PsiPackage) return value
    return null
  }
}