// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractShowUsagesActionCompletionCommand
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

internal class JavaShowUsagesActionCompletionCommand : AbstractShowUsagesActionCompletionCommand() {
  override fun hasToShow(element: PsiElement): Boolean {
    val namedIdentifierParent = PsiTreeUtil.getParentOfType(element, PsiNameIdentifierOwner::class.java)
    return namedIdentifierParent?.nameIdentifier == element &&
           (namedIdentifierParent is PsiVariable || namedIdentifierParent is PsiMethod || namedIdentifierParent is PsiMember)

  }
}