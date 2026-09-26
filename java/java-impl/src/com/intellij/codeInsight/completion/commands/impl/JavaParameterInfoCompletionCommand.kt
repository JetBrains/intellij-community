// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractParameterInfoCompletionCommand
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.parentOfType

public class JavaParameterInfoCompletionCommand : AbstractParameterInfoCompletionCommand() {
  override fun inParameterList(offset: Int, psiFile: PsiFile): Boolean {
    val element = psiFile.findElementAt(offset)
    val parameterList = element?.parentOfType<PsiExpressionList>() ?: return false
    if (parameterList.parent !is PsiMethodCallExpression) return false
    return parameterList.textRange.startOffset < offset && offset < parameterList.textRange.endOffset
  }
}