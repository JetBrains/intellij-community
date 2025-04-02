// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.ApplicableCompletionCommand
import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CommandProvider
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.CompletionCommandWithPreview
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.completion.command.getDataContext
import com.intellij.codeInsight.completion.command.getTargetContext
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import javax.swing.Icon


open class ActionCommandProvider(
  @field:Language("devkit-action-id") var actionId: String,
  val name: String,
  val i18nName: @Nls String,
  val icon: Icon? = null,
  val priority: Int? = null,
  val previewText: @Nls String?,
) : CommandProvider, DumbAware {

  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    if (!isApplicable(context.offset, context.psiFile, context.editor)) return emptyList()
    val element = createCommand(context) ?: return emptyList()
    return listOf(element)
  }

  protected open fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? =
    ActionCompletionCommand(actionId = actionId,
                            name = name,
                            i18nName = i18nName,
                            icon = icon,
                            priority = priority,
                            previewText = previewText)

  protected open fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val action: AnAction? = ActionManager.getInstance().getAction(actionId)
    if (action == null || editor == null) return false
    if (!DumbService.Companion.getInstance(psiFile.project).isUsableInCurrentContext(action)) return false
    val context = getTargetContext(offset, editor)
    val dataContext = getDataContext(psiFile, editor, context)
    val presentation: Presentation = action.templatePresentation.clone()
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION,
                                          ActionUiKind.Companion.NONE, null)
    if (ActionUtil.performDumbAwareUpdate(action, event, false)) {
      return false
    }
    return event.presentation.isEnabled && event.presentation.isVisible
  }

  /**
   * Determines if the action associated with this command can be applied to the given project context
   * by analyzing the specified offset and the PsiFile structure.
   * It is expected to be called outside any meaningful PsiElements
   */
  protected fun isApplicableToProject(offset: Int, psiFile: PsiFile): Boolean {
    if (offset - 1 < 0) return true
    val element = psiFile.findElementAt(offset - 1)
    if (element is PsiComment) return true
    val fileDocument = psiFile.fileDocument
    val lineNumber = fileDocument.getLineNumber(offset)
    val lineStartOffset = fileDocument.getLineStartOffset(lineNumber)
    for (ch in fileDocument.immutableCharSequence.subSequence(lineStartOffset, offset)) {
      if (!ch.isWhitespace()) return false
      if (ch == '\n') return true
    }
    return true
  }
}

/**
 * Represents an action completion command that triggers a specific IDE action
 * identified by its `actionId`. This class allows integration of IntelliJ Platform actions
 * as part of code completion features, ensuring that the action is applicable and
 * executable within a given code editor context.
 */
open class ActionCompletionCommand(
  @field:Language("devkit-action-id") var actionId: String,
  override val name: String,
  override val i18nName: @Nls String,
  private val previewText: @Nls String?,
  override val icon: Icon? = null,
  override val priority: Int? = null,
  override val highlightInfo: HighlightInfoLookup? = null,
) : CompletionCommand(), DumbAware, CompletionCommandWithPreview {
  private val action: AnAction? = ActionManager.getInstance().getAction(actionId)

  override val additionalInfo: String?
    get() {
      val shortcutText = KeymapUtil.getFirstKeyboardShortcutText(actionId)
      if (shortcutText.isNotEmpty()) {
        return shortcutText
      }
      return null
    }

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    val action = action ?: return
    if (editor == null) return
    val dataContext = DataManager.getInstance().getDataContext(editor.getComponent())
    val presentation: Presentation = action.templatePresentation.clone()
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION,
                                          ActionUiKind.Companion.NONE, null)
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event)
    }
  }

  override fun getPreview(): IntentionPreviewInfo? {
    if (previewText == null) return null
    return IntentionPreviewInfo.Html(previewText)
  }
}

/**
 * Represents an abstract action completion command that triggers a specific IDE action
 * identified by its `actionId`. This class allows integration of IntelliJ Platform actions
 * as part of code completion features, ensuring that the action is applicable and
 * executable within a given code editor context.
 */
@Deprecated("Use providers instead")
abstract class AbstractActionCompletionCommand(
  @Language("devkit-action-id") var actionId: String,
  override val name: String,
  override val i18nName: @Nls String,
  override val icon: Icon?,
  override val priority: Int? = null,
) : ApplicableCompletionCommand(), DumbAware {
  private val action: AnAction? = ActionManager.getInstance().getAction(actionId)

  override val additionalInfo: String?
    get() {
      val shortcutText = KeymapUtil.getFirstKeyboardShortcutText(actionId)
      if (shortcutText.isNotEmpty()) {
        return shortcutText
      }
      return null
    }

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val action = action ?: return false
    if (editor == null) return false
    if (!DumbService.Companion.getInstance(psiFile.project).isUsableInCurrentContext(action)) return false
    val context = getTargetContext(offset, editor)
    val dataContext = getDataContext(psiFile, editor, context)
    val presentation: Presentation = action.templatePresentation.clone()
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION,
                                          ActionUiKind.Companion.NONE, null)
    if (ActionUtil.performDumbAwareUpdate(action, event, false)) {
      return false
    }
    return event.presentation.isEnabled && event.presentation.isVisible
  }

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    val action = action ?: return
    if (editor == null) return
    val dataContext = DataManager.getInstance().getDataContext(editor.getComponent())
    val presentation: Presentation = action.templatePresentation.clone()
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION,
                                          ActionUiKind.Companion.NONE, null)
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event)
    }
  }

  /**
   * Determines if the action associated with this command can be applied to the given project context
   * by analyzing the specified offset and the PsiFile structure.
   * It is expected to be called outside any meaningful PsiElements
   */
  protected fun isApplicableToProject(offset: Int, psiFile: PsiFile): Boolean {
    if (offset - 1 < 0) return true
    val element = psiFile.findElementAt(offset - 1)
    if (element is PsiComment) return true
    val fileDocument = psiFile.fileDocument
    val lineNumber = fileDocument.getLineNumber(offset)
    val lineStartOffset = fileDocument.getLineStartOffset(lineNumber)
    for (ch in fileDocument.immutableCharSequence.subSequence(lineStartOffset, offset)) {
      if (!ch.isWhitespace()) return false
      if (ch == '\n') return true
    }
    return true
  }
}