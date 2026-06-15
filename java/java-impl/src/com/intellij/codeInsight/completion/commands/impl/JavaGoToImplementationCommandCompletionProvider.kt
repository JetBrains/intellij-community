// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.commands.AbstractGoToImplementationCompletionCommandProvider
import com.intellij.codeInsight.completion.command.commands.ActionCompletionCommand
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaTokenType
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaToken
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
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
      val currentOffset = if (element is PsiJavaToken && element.tokenType == JavaTokenType.SEMICOLON) {
        offset - 1
      }
      else {
        offset
      }
      if (!TextRange(member.textRange.startOffset, member.body?.textRange?.startOffset ?: member.textRange.endOffset)
          .contains(currentOffset)
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

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    val currentElement = getCommandContext(context.offset, context.psiFile)
    var targetContext = context
    if (currentElement is PsiJavaToken && currentElement.tokenType == JavaTokenType.SEMICOLON) {
      var currentOffset = context.offset - 1
      if (getCommandContext(context.offset - 1, context.psiFile)?.textMatches(")") == true) {
        currentOffset = context.offset - 3
      }
      targetContext = context.copy(offset = currentOffset)
    }
    return super.createCommandWithNameIdentifierAndLastAdjusted(targetContext)
  }
}
