// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractRenameActionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType

public class JavaRenameActionCommandProvider: AbstractRenameActionCommandProvider() {
  override fun findRenameOffset(offset: Int, psiFile: PsiFile): Int? {
    var currentOffset = offset
    if (currentOffset == 0) return null
    var element = getCommandContext(currentOffset, psiFile) ?: return null
    if (element is PsiWhiteSpace) {
      element = PsiTreeUtil.prevVisibleLeaf(element) ?: return null
      currentOffset = element.textRange.endOffset
    }

    //
    //void something..(String a)..{
    //
    // }..
    //<..> place to call 'rename'
    val method = element.parentOfType<PsiMethod>()
    if (method != null &&
        (method.identifyingElement?.textRange?.endOffset == currentOffset ||
        method.parameterList.textRange?.endOffset == currentOffset ||
        method.body?.rBrace?.textRange?.endOffset == currentOffset)) return method.identifyingElement?.textRange?.endOffset

    val psiClass = element.parentOfType<PsiClass>()
    if (psiClass != null && psiClass.rBrace != null && psiClass.rBrace?.textRange?.endOffset == currentOffset) {
      return psiClass.identifyingElement?.textRange?.endOffset
    }
    return offset
  }
}