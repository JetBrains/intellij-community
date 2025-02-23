// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * This command enables triggering the "Recent Files" action during code completion.
 */
internal class RecentFilesCompletionCommand : AbstractActionCompletionCommand("RecentFiles",
                                                                     "Recent files",
                                                                     CodeInsightBundle.message("command.completion.recent.files.text"),
                                                                     null,
                                                                     -150) {
  override fun supportsReadOnly(): Boolean  = true

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if(!super.isApplicable(offset, psiFile, editor)) return false
    return isApplicableToProject(offset, psiFile)
  }
}
