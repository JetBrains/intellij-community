// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CommandProvider
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.ide.DataManager
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls

abstract class AbstractSurroundWithCompletionCommandProvider : CommandProvider {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    if (!isApplicable(context.offset, context.psiFile, context.editor)) return emptyList()
    val commands = mutableListOf<CompletionCommand>()
    val actions = SurroundWithHandler.buildSurroundActions(context.project, context.editor, context.psiFile) ?: return emptyList()
    for (action in actions) {
      if (action !is SurroundWithHandler.InvokeSurrounderAction) {
        continue
      }
      val surrounder = action.surrounder
      if (!isApplicable(context.offset, context.psiFile, context.editor, surrounder)) continue
      val elements = action.elements
      if (elements.size == 1 && elements[0].textRange.endOffset != context.offset) continue
      commands.add(object : CompletionCommand() {
        override val highlightInfo: HighlightInfoLookup?
          get() = if (elements.size == 1) {
            HighlightInfoLookup(elements[0].textRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
          }
          else {
            null
          }

        override val additionalInfo: String?
          get() {
            val shortcutText = KeymapUtil.getFirstKeyboardShortcutText("SurroundWith")
            if (shortcutText.isNotEmpty()) {
              return shortcutText
            }
            return null
          }

        override val presentableName: @Nls String
          get() = CodeInsightBundle.message("command.completion.surround.with.text", surrounder.templateDescription)

        override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
          if (editor == null) return

          val targetElements = elements.map {
            PsiTreeUtil.findSameElementInCopy(it, psiFile)
          }.toTypedArray()

          val targetAction = SurroundWithHandler.InvokeSurrounderAction(action.surrounder, editor.project, editor, targetElements, 'a')

          val dataContext = DataManager.getInstance().getDataContext(editor.getComponent())
          val presentation: Presentation = targetAction.templatePresentation.clone()
          val event = AnActionEvent.createEvent(targetAction, dataContext, presentation, ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION,
                                                ActionUiKind.NONE, null)

          ActionUtil.performAction(action, event)
        }

        override fun getPreview(): IntentionPreviewInfo {
          return action.preview
        }
      })
    }
    return commands
  }

  abstract fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?, surrounder: Surrounder): Boolean
  abstract fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean
}