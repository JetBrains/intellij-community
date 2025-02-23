// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.idea.ActionsBundle
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * A command that triggers the "Optimize Imports" action within the context of code completion.
 */
internal class OptimizeImportCompletionCommand : AbstractActionCompletionCommand("OptimizeImports",
                                                                        "Optimize imports",
                                                                        ActionsBundle.message("action.OptimizeImports.text"),
                                                                        null,
                                                                        -100) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    if (InjectedLanguageManager.getInstance(psiFile.project).isInjectedFragment(psiFile)) return false
    return isApplicableToProject(offset, psiFile)
  }
}