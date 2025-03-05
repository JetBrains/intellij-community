// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractGenerateCommandProvider
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier

internal class JavaGenerateCommandCompletion : AbstractGenerateCommandProvider() {
  override fun generationIsAvailable(element: PsiElement, offset: Int): Boolean {
    val parent = element.parent
    if (parent !is PsiClass) return false
    if (!isOnlySpaceInLine(element.containingFile.fileDocument, offset) &&
        !(element is PsiIdentifier && parent.nameIdentifier == element)) return false
    return true
  }
}