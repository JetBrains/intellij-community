// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractShowUsagesActionCompletionCommandProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil

internal class JavaShowUsagesActionCompletionCommandProvider : AbstractShowUsagesActionCompletionCommandProvider() {
  override fun hasToShow(element: PsiElement): Boolean {
    val namedIdentifierParent = PsiTreeUtil.getParentOfType(element, PsiNameIdentifierOwner::class.java)
    return namedIdentifierParent?.nameIdentifier == element &&
           (namedIdentifierParent is PsiVariable || namedIdentifierParent is PsiMethod || namedIdentifierParent is PsiMember)

  }
}