// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.completion.command.*
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.idea.ActionsBundle
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import java.util.Locale.getDefault

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
    val element = createCommand(context) ?: return emptyList()
    return listOf(element)
  }

  abstract fun createCommand(context: CommandCompletionProviderContext): CompletionCommand?
}

abstract class AbstractFormatCodeCompletionCommand : CompletionCommand(), CompletionCommandWithPreview {
  final override val synonyms: List<String>
    get() = listOf("Format")

  @Suppress("HardCodedStringLiteral")
  final override val presentableName: @Nls String
    get() = ActionsBundle.message("action.ReformatCode.text")
      .replaceFirst("_", "")
      .lowercase()
      .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }

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