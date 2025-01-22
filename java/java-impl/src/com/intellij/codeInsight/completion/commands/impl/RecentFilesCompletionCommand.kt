// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.java.JavaBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class RecentFilesCompletionCommand : AbstractActionCompletionCommand("RecentFiles",
                                                                     "Recent files",
                                                                     JavaBundle.message("command.completion.recent.files.text"),
                                                                     null,
                                                                     -150) {
  override fun supportsReadOnly(): Boolean  = true

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if(!super.isApplicable(offset, psiFile, editor)) return false
    return isApplicableToProject(offset, psiFile)
  }
}
