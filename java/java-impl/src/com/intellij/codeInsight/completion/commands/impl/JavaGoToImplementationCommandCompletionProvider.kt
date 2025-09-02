// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractGoToImplementationCompletionCommandProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil

internal class JavaGoToImplementationCommandCompletionProvider : AbstractGoToImplementationCompletionCommandProvider() {
  override fun canGoToImplementation(element: PsiElement, offset: Int): Boolean {

    val member = PsiTreeUtil.getParentOfType(element, PsiMember::class.java) ?: return false
    if (member is PsiClass) {
      if (!TextRange(member.textRange.startOffset, member.lBrace?.textRange?.startOffset ?: member.textRange.endOffset)
          .contains(offset)
      ) {
        return false
      }
    }
    else if (member is PsiMethod) {
      if (!TextRange(member.textRange.startOffset, member.body?.textRange?.startOffset ?: member.textRange.endOffset)
          .contains(offset)
      ) {
        return false
      }
    }
    else {
      return false
    }
    val clazz = member as? PsiClass ?: member.containingClass ?: return false

    if (clazz.hasModifierProperty(PsiModifier.FINAL)) return false

    if (ClassInheritorsSearch.search(clazz, false).findFirst() == null &&
        !(LambdaUtil.isFunctionalClass(clazz) &&
          ReferencesSearch.search(clazz).findFirst() != null)) {
      return false
    }

    return true
  }
}
