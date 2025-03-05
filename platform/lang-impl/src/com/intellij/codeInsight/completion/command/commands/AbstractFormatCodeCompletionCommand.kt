// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.completion.command.ApplicableCompletionCommand
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * An abstract command designed to provide a context-specific code formatting completion action.
 * This class handles the completion logic for reformatting code based on the PSI (Program Structure Interface)
 * context and specific applicability conditions.
 */
abstract class AbstractFormatCodeCompletionCommand : ApplicableCompletionCommand() {
  final override val name: String
    get() = "Format"

  final override val i18nName: @Nls String
    get() = ActionsBundle.message("action.ReformatCode.text")

  final  override val icon: Icon
    get() = AllIcons.Actions.ReformatCode // Use the reformat icon

  final override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (InjectedLanguageManager.getInstance(psiFile.project).isInjectedFragment(psiFile)) return false
    return true
  }

  final override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    val element = getContext(offset, psiFile) ?: return
    val target = findTargetToRefactor(element)
    ReformatCodeProcessor(element.containingFile, arrayOf(target.textRange)).run()
  }

  /**
   * Finds the appropriate parent element for refactoring based on the given PSI element.
   */
  abstract fun findTargetToRefactor(element: PsiElement): PsiElement
}