// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.commands.AbstractSafeDeleteCompletionCommandProvider
import com.intellij.codeInsight.completion.command.commands.DirectInspectionFixCompletionCommand
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.codeInsight.completion.command.getTargetContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType

public class JavaSafeDeleteCompletionCommandProvider : AbstractSafeDeleteCompletionCommandProvider() {
  override fun findElement(offset: Int, psiFile: PsiFile, editor: Editor?): PsiElement? {
    if (editor == null) return null
    var element = getCommandContext(offset, psiFile) ?: return null
    if (element is PsiWhiteSpace) element = PsiTreeUtil.skipWhitespacesBackward(element) ?: return null
    if (element is PsiIdentifier && (element.parent is PsiMember || element.parent is PsiVariable)) {
      return element
    }
    if (element.elementType == JavaTokenType.RBRACE) {
      val parentElement = element.parent
      if (parentElement is PsiMember) {
        val targetElement = (if (parentElement is PsiNameIdentifierOwner) parentElement.nameIdentifier
        else PsiTreeUtil.findChildOfType(parentElement, PsiIdentifier::class.java)) ?: return null
        return targetElement
      }
      val grandparentElement = element.parent?.parent
      if (grandparentElement is PsiMember) {
        val targetElement = (if (grandparentElement is PsiNameIdentifierOwner) grandparentElement.nameIdentifier
        else PsiTreeUtil.findChildOfType(grandparentElement, PsiIdentifier::class.java)) ?: return null
        return targetElement
      }
    }
    val targetContext = getTargetContext(offset, editor)
    if (targetContext?.isWritable != true) return null
    return element
  }

  override fun skipForHighlightCommand(command: CompletionCommand): Boolean {
    return command is DirectInspectionFixCompletionCommand && command.inspectionId == "unused"
  }
}