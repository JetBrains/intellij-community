// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.CommandProvider
import com.intellij.codeInsight.completion.commands.api.CompletionCommand
import com.intellij.codeInsight.completion.commands.core.CommandCompletionService
import com.intellij.codeInsight.completion.commands.core.CommandCompletionUnsupportedOperationException
import com.intellij.codeInsight.completion.commands.core.HighlightingContainer
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass.IntentionsInfo
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix
import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewUnsupportedOperationException
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.icons.AllIcons
import com.intellij.modcommand.ModHighlight
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import javax.swing.Icon

class IntentionCommandProvider : CommandProvider {
  override fun getCommands(
    project: Project,
    editor: Editor,
    offset: Int,
    psiFile: PsiFile,
    originalEditor: Editor,
    originalOffset: Int,
    originalFile: PsiFile,
  ): List<CompletionCommand> {

    // Obtain available intentions for the context
    return getAvailableIntentions(originalEditor, originalFile, offset, editor, psiFile)
  }

  override fun getId(): String {
    return "IntentionCommandProvider"
  }


  private fun getAvailableIntentions(
    originalEditor: Editor,
    originalFile: PsiFile,
    offset: Int,
    editor: Editor,
    psiFile: PsiFile,
  ): List<IntentionCompletionCommand> {
    val container: HighlightingContainer = getPreviousHighlighters(originalEditor, psiFile.fileDocument, offset) ?: return emptyList()

    val intentionsCache = getIntentions(editor, psiFile, originalFile)

    val intentions = mutableListOf<IntentionActionWithTextCaching>()
    val errorFixes = container.cachedIntentions.errorFixes
    intentions.addAll(errorFixes)
    val inspectionFixes = container.cachedIntentions.inspectionFixes
    intentions.addAll(inspectionFixes)
    intentions.addAll(intentionsCache.intentions)

    val result: MutableList<IntentionCompletionCommand> = ArrayList()
    for (intention in intentions) {
      if (intention.action is EmptyIntentionAction) continue
      if (intention.action.asModCommandAction() == null &&
          IntentionActionDelegate.unwrap(intention.action) !is LocalQuickFixAndIntentionActionOnPsiElement &&
          IntentionActionDelegate.unwrap(intention.action) !is CreateFromUsageBaseFix) continue
      val highlighting: RangeHighlighterEx? = container.map[intention]
      var icon: Icon? = intention.icon
      var priority: Int? = null
      var attributesKey: TextAttributesKey? = null
      if (icon == null) {
        if (intention in errorFixes) {
          icon = AllIcons.Actions.QuickfixBulb
          priority = 100
          attributesKey = CodeInsightColors.ERRORS_ATTRIBUTES
        }
        else if (intention in inspectionFixes) {
          icon = AllIcons.Actions.IntentionBulb
          priority = 90
          attributesKey = CodeInsightColors.WARNINGS_ATTRIBUTES
        }
        else {
          icon = AllIcons.Actions.IntentionBulbGrey
        }
      }
      result.add(IntentionCompletionCommand(intention, priority, icon,
                                            attributesKey?.let {
                                              highlighting?.let {
                                                ModHighlight.HighlightInfo(TextRange(it.startOffset, it.endOffset),
                                                                           attributesKey, false)
                                              } ?: intention.fixRange?.let { ModHighlight.HighlightInfo(it, attributesKey, false) }
                                            }
      ))
    }
    return result
  }

  private fun getIntentions(
    editor: Editor,
    psiFile: PsiFile,
    originalFile: PsiFile,
  ): CachedIntentions {

    val availableIntentions = IntentionManager.getInstance().getAvailableIntentions(mutableListOf(originalFile.language.id))

    val actionsToShow = IntentionsInfo()
    for (action in availableIntentions) {
      val descriptor =
        HighlightInfo.IntentionActionDescriptor(action, null, null, null, null, null, null, null);
      actionsToShow.intentionsToShow.add(descriptor)
    }

    val intentionsCache = CachedIntentions(originalFile.project, psiFile, editor)

    var toRemove = mutableListOf<HighlightInfo.IntentionActionDescriptor>()
    for (intention in actionsToShow.intentionsToShow) {
      try {
        if (!intention.action.isAvailable(originalFile.project, editor, psiFile)) {
          toRemove.add(intention)
        }
      }
      catch (_: UnsupportedOperationException) {
        toRemove.add(intention)
      }
      catch (_: CommandCompletionUnsupportedOperationException) {
        toRemove.add(intention)
      }
      catch (_: IntentionPreviewUnsupportedOperationException) {
        toRemove.add(intention)
      }
    }
    actionsToShow.intentionsToShow.removeAll(toRemove)
    intentionsCache.wrapAndUpdateActions(actionsToShow, false)
    return intentionsCache
  }
}

private fun getPreviousHighlighters(originalEditor: Editor, targetDocument: Document, offset: Int): HighlightingContainer? {
  val commandCompletionService = originalEditor.project?.service<CommandCompletionService>() ?: return null
  return commandCompletionService.getPreviousHighlighting(originalEditor, targetDocument, offset)
}