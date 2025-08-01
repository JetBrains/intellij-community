// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractCopyClassCompletionCommandProvider
import com.intellij.codeInsight.completion.command.commands.AbstractMoveCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.*
import com.intellij.psi.PsiModifier.STATIC
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentOfType

internal class JavaMoveCompletionCommandProvider : AbstractMoveCompletionCommandProvider() {
  override fun findMoveClassOffset(offset: Int, psiFile: PsiFile): Int? {
    var currentOffset = offset
    if (currentOffset == 0) return null
    var element = getCommandContext(offset, psiFile) ?: return null
    if (element is PsiWhiteSpace) {
      element = PsiTreeUtil.skipWhitespacesBackward(element) ?: return null
    }
    currentOffset = element.textRange?.endOffset ?: currentOffset
    val clazz = element.findParentOfType<PsiClass>()
    val lBrace = clazz?.lBrace
    if (lBrace != null && (clazz.textRange.endOffset == currentOffset ||
                           (clazz.lBrace?.textRange?.startOffset ?: 0) > currentOffset)) {
      return clazz.nameIdentifier?.textRange?.endOffset
    }
    val method = element.findParentOfType<PsiMethod>()
    val methodLBrace = method?.body?.lBrace
    if (methodLBrace != null && (method.textRange.endOffset == currentOffset ||
                                 (methodLBrace.textRange?.startOffset ?: 0) > currentOffset)) {
      return method.nameIdentifier?.textRange?.endOffset
    }
    val field = element.findParentOfType<PsiField>()
    if (field != null &&
        field.hasModifierProperty(STATIC) &&
        (field.textRange.endOffset == currentOffset ||
                          (field.identifyingElement?.textRange?.endOffset ?: 0) == currentOffset)) {
      return field.identifyingElement?.textRange?.endOffset
    }
    return null
  }
}

public class JavaCopyCompletionCommandProvider : AbstractCopyClassCompletionCommandProvider() {
  override fun findMoveClassOffset(offset: Int, psiFile: PsiFile): Int? {
    var currentOffset = offset
    if (currentOffset == 0) return null
    var element = getCommandContext(offset, psiFile) ?: return null
    if (element is PsiWhiteSpace) {
      element = PsiTreeUtil.skipWhitespacesBackward(element) ?: return null
    }
    currentOffset = element.textRange?.endOffset ?: currentOffset
    val clazz = element.findParentOfType<PsiClass>()
    val lBrace = clazz?.lBrace
    if (lBrace != null && (clazz.textRange.endOffset == currentOffset ||
                           (clazz.lBrace?.textRange?.startOffset ?: 0) > currentOffset)) {
      return clazz.nameIdentifier?.textRange?.endOffset
    }
    return null
  }
}