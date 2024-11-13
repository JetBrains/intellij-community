// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.api

import com.intellij.modcommand.ModHighlight.HighlightInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
abstract class CompletionCommand {
  companion object {
    val EP_NAME = ExtensionPointName<CompletionCommand>("com.intellij.codeInsight.completion.command")
  }

  abstract val name: String
  abstract val icon: Icon?
  open val priority: Int? = null
  abstract fun isApplicable(offset: Int, psiFile: PsiFile): Boolean
  abstract fun execute(offset: Int, psiFile: PsiFile)
  open val highlightInfo: HighlightInfo? = null

  open fun getContext(offset: Int, psiFile: PsiFile): PsiElement? {
    return (if (offset == 0) psiFile.findElementAt(offset) else psiFile.findElementAt(offset - 1))
  }
}

@Deprecated(message = "Used only to adapt old style actions", level = DeprecationLevel.WARNING)
abstract class OldCompletionCommand : CompletionCommand() {
  abstract fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean
  abstract fun execute(offset: Int, psiFile: PsiFile, editor: Editor?)

  override fun isApplicable(offset: Int, psiFile: PsiFile): Boolean {
    return isApplicable(offset, psiFile, null)
  }

  override fun execute(offset: Int, psiFile: PsiFile) {
    execute(offset, psiFile, null)
  }
}
