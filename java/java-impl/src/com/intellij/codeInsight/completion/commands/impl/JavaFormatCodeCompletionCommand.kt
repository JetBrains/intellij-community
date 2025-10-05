// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.completion.command.commands.AbstractFormatCodeCompletionCommand
import com.intellij.codeInsight.completion.command.commands.AbstractFormatCodeCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.*
import com.intellij.psi.util.parents

internal class JavaFormatCodeCompletionCommandProvider : AbstractFormatCodeCompletionCommandProvider() {
  override fun createCommand(context: CommandCompletionProviderContext): CompletionCommand? {
    val element = getCommandContext(context.offset, context.psiFile) ?: return null
    val targetElement = findTargetToRefactorInner(element)
    val highlightInfoLookup = HighlightInfoLookup(targetElement.textRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
    val command = object : JavaFormatCodeCompletionCommand(context){
      override val highlightInfo: HighlightInfoLookup
        get() {
          return highlightInfoLookup
        }
    }
    return command
  }
}

private fun findTargetToRefactorInner(element: PsiElement): PsiElement {
  var parentElement = (element.parents(true).firstOrNull { it is PsiMember || it is PsiCodeBlock || it is PsiStatement }
                       ?: element.containingFile
                       ?: element)
  if(parentElement.parent is PsiMethod) parentElement = parentElement.parent!!
  return parentElement
}

internal abstract class JavaFormatCodeCompletionCommand(context: CommandCompletionProviderContext) : AbstractFormatCodeCompletionCommand(context) {
  override fun findTargetToRefactor(element: PsiElement): PsiElement {
    return findTargetToRefactorInner(element)
  }
}