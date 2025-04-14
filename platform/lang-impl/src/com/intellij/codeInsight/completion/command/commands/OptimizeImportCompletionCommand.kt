// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.idea.ActionsBundle
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

internal class OptimizeImportCompletionCommandProvider :
  ActionCommandProvider(actionId = "OptimizeImports",
                        name = "Optimize imports",
                        i18nName = ActionsBundle.message("action.OptimizeImports.text"),
                        icon = null,
                        priority = -100,
                        previewText = ActionsBundle.message("action.OptimizeImports.description")) {

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    if (InjectedLanguageManager.getInstance(psiFile.project).isInjectedFragment(psiFile)) return false
    return isApplicableToProject(offset, psiFile)
  }
}