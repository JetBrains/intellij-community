// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CommandProvider
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.idea.ActionsBundle
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls

/**
 * Base for completion commands that inline a variable-like element (e.g. a Java local variable or field, a Kotlin property) by
 * delegating to a registered [InlineActionHandler].
 *
 * Subclasses only locate the element to inline at the caret ([findElementToInline]) and provide the command's [presentableName];
 * the availability gate, the highlight, and the command that performs the inline are shared.
 */
abstract class AbstractInlineVariableCompletionCommandProvider : CommandProvider {

  /** Text shown in the lookup for this command (e.g. "Inline", "Inline Property"). */
  protected abstract val presentableName: @Nls String

  /**
   * Returns the element to inline when the caret is at [offset], or `null` if inlining is not applicable here.
   *
   * Invoked under a read action; implementations must not start their own progress or thread switching. The caller
   * provides the appropriate threading context: the availability check runs it directly on a background thread, while
   * the execute path wraps it in a modal progress.
   */
  protected abstract fun findElementToInline(offset: Int, psiFile: PsiFile, editor: Editor?): PsiElement?

  /** Range highlighted while the command is selected. Defaults to the element under the caret. */
  protected open fun getHighlightRange(offset: Int, psiFile: PsiFile): TextRange? {
    var element = getCommandContext(offset, psiFile) ?: return null
    if (element is PsiWhiteSpace) {
      element = PsiTreeUtil.skipWhitespacesBackward(element) ?: return null
    }
    return element.textRange
  }

  final override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    val element = findElementToInline(context.offset, context.psiFile, context.editor) ?: return emptyList()
    val editor = context.editor
    val canInline = InlineActionHandler.EP_NAME.extensionList.any { extension ->
      try {
        extension.canInlineElementInEditor(element, editor)
      }
      catch (_: Exception) {
        false
      }
    }
    if (!canInline) return emptyList()
    val highlightInfo = getHighlightRange(context.offset, context.psiFile)?.let {
      HighlightInfoLookup(it, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
    }
    return listOf(InlineVariableCompletionCommand(presentableName, highlightInfo))
  }

  private inner class InlineVariableCompletionCommand(
    override val presentableName: @Nls String,
    override val highlightInfo: HighlightInfoLookup?,
  ) : CompletionCommand(), DumbAware {

    override val synonyms: List<String>
      get() = listOf("inline", "insert")

    override val additionalInfo: String?
      get() = KeymapUtil.getFirstKeyboardShortcutText("Inline").takeIf { it.isNotEmpty() }

    override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
      if (editor == null) return
      WriteIntentReadAction.run {
        val element = runWithModalProgressBlocking(ModalTaskOwner.project(psiFile.project), presentableName) {
          readAction { findElementToInline(offset, psiFile, editor) }
        } ?: return@run

        for (extension in InlineActionHandler.EP_NAME.extensionList) {
          if (extension.canInlineElement(element)) {
            extension.inlineElement(psiFile.project, editor, element)
            return@run
          }
        }
      }
    }

    override fun getPreview(): IntentionPreviewInfo {
      return IntentionPreviewInfo.Html(ActionsBundle.message("action.Inline.description"))
    }
  }
}
