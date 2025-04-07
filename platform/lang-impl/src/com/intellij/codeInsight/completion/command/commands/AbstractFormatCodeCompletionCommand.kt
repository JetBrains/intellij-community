// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.completion.command.*
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
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
abstract class AbstractFormatCodeCompletionCommandProvider :
  CommandProvider {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    val psiFile = context.psiFile
    if (InjectedLanguageManager.getInstance(psiFile.project).isInjectedFragment(psiFile)) return emptyList()
    return listOf(createCommand(context))
  }

  abstract fun createCommand(context: CommandCompletionProviderContext): CompletionCommand
}

abstract class AbstractFormatCodeCompletionCommand : CompletionCommand(), CompletionCommandWithPreview {
  final override val name: String
    get() = "Format"

  final override val i18nName: @Nls String
    get() = ActionsBundle.message("action.ReformatCode.text")
  override val synonyms: List<String>
    get() = listOf("Reformat")
  final override val icon: Icon
    get() = AllIcons.Actions.ReformatCode // Use the reformat icon

  override fun getPreview(): IntentionPreviewInfo? {
    return IntentionPreviewInfo.Html(ActionsBundle.message("action.ReformatCode.description"))
  }

  final override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    val element = getCommandContext(offset, psiFile) ?: return
    val target = findTargetToRefactor(element)
    ReformatCodeProcessor(element.containingFile, arrayOf(target.textRange)).run()
  }

  /**
   * Finds the appropriate parent element for refactoring based on the given PSI element.
   */
  abstract fun findTargetToRefactor(element: PsiElement): PsiElement
}