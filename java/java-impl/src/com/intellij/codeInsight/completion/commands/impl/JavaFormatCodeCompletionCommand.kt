// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractFormatCodeCompletionCommand
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiStatement
import com.intellij.psi.util.parents

internal class JavaFormatCodeCompletionCommand : AbstractFormatCodeCompletionCommand() {
  override fun findTargetToRefactor(element: PsiElement): PsiElement {
    return element.parents(true).first { it is PsiMember || it is PsiCodeBlock || it is PsiStatement }
  }
}