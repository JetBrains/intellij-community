// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractExtractConstantCompletionCommandProvider
import com.intellij.codeInsight.completion.command.commands.AbstractExtractFieldCompletionCommandProvider
import com.intellij.codeInsight.completion.command.commands.AbstractExtractParameterCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentOfType

public class JavaExtractConstantCompletionCommandProvider : AbstractExtractConstantCompletionCommandProvider() {
  override fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int? {
    return findOffsetForLocalVariable(offset, psiFile)
  }
}

public class JavaExtractFieldCompletionCommandProvider : AbstractExtractFieldCompletionCommandProvider() {
  override fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int? {
    return findOffsetForLocalVariable(offset, psiFile)
  }
}

public class JavaExtractParameterCompletionCommandProvider : AbstractExtractParameterCompletionCommandProvider() {
  override fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int? {
    return findOffsetForLocalVariable(offset, psiFile)
  }
}

private fun findOffsetForLocalVariable(offset: Int, psiFile: PsiFile): Int? {
  var currentOffset = offset
  if (currentOffset == 0) return null
  var element = getCommandContext(offset, psiFile) ?: return null
  if (element is PsiWhiteSpace) {
    element = PsiTreeUtil.skipWhitespacesBackward(element) ?: return null
  }
  currentOffset = element.textRange?.endOffset ?: currentOffset
  val localVariable = element.findParentOfType<PsiLocalVariable>() ?: return null
  if (localVariable.textRange.endOffset == currentOffset ||
      localVariable.textRange.endOffset - 1 == currentOffset ||
      localVariable.identifyingElement?.textRange?.endOffset == currentOffset) {
    return currentOffset
  }
  return null
}
