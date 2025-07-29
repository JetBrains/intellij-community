// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.*
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import java.util.Locale.getDefault
import javax.swing.Icon


/**
 * Provides completion commands that execute IDE actions identified by their action IDs.
 * This provider creates commands that integrate with IntelliJ Platform's action system,
 * allowing actions to be triggered through code completion features.
 *
 * @property actionId The unique identifier of the IDE action to be executed
 * @property presentableName The display internationalized name of the command
 * @property icon Optional icon to be displayed with the command
 * @property priority Optional priority value affecting command ordering
 * @property previewText Optional preview text shown when the command is selected
 */
open class ActionCommandProvider(
  @field:Language("devkit-action-id") var actionId: String,
  val presentableName: @Nls String,
  val icon: Icon? = null,
  val priority: Int? = null,
  val previewText: @Nls String?,
  val synonyms: List<String> = emptyList()
) : CommandProvider {

  /**
   * Creates and returns a list of completion commands based on the provided context.
   * The method checks if the action is applicable in the current context and creates
   * the appropriate command if conditions are met.
   *
   * @param context The context containing information about the completion environment
   * @return A list of completion commands, empty if the action is not applicable
   */
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    if (!isApplicable(context.offset, context.psiFile, context.editor)) return emptyList()
    val element = createCommand(context) ?: return emptyList()
    return listOf(element)
  }

  
  /**
   * Creates a new action completion command based on the provided context.
   * This method instantiates an [ActionCompletionCommand] with the provider's configuration
   * including action ID, name, icon, and preview settings.
   *
   * @param context The context containing information about the completion environment
   * @return A new [ActionCompletionCommand] instance, or null if the command cannot be created
   */
  protected open fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? =
    ActionCompletionCommand(actionId = actionId,
                            presentableActionName = presentableName,
                            icon = icon,
                            priority = priority,
                            previewText = previewText,
                            synonyms = synonyms)

  /**
   * Checks whether the action associated with this provider is applicable in the current context.
   *
   * @param offset The caret offset in the editor
   * @param psiFile The PSI file being edited
   * @param editor The current editor instance, may be null
   * @return true if the action can be applied in the current context, false otherwise
   */
  protected open fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val action: AnAction? = ActionManager.getInstance().getAction(actionId)
    if (action == null || editor == null) return false
    if (!DumbService.getInstance(psiFile.project).isUsableInCurrentContext(action)) return false
    val context = getTargetContext(offset, editor)
    val dataContext = getDataContext(psiFile, editor, context)
    val presentation: Presentation = action.templatePresentation.clone()
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION,
                                          ActionUiKind.NONE, null)
    val result = ActionUtil.updateAction(action, event)
    if (!result.isPerformed) {
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

  protected fun createCommandWithNameIdentifier(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    var element = getCommandContext(context.offset, context.psiFile) ?: return null
    if (element is PsiNameIdentifierOwner) {
      element = element.nameIdentifier ?: return null
    }
    val range = element.textRange ?: return null
    return ActionCompletionCommand(actionId = actionId,
                                   synonyms = synonyms,
                                   presentableActionName = presentableName,
                                   icon = icon,
                                   priority = priority,
                                   previewText = previewText,
                                   highlightInfo = HighlightInfoLookup(range, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0))
  }
}

/**
 * Represents an action completion command that triggers a specific IDE action
 * identified by its `actionId`. This class allows integration of IntelliJ Platform actions
 * as part of code completion features, ensuring that the action is applicable and
 * executable within a given code editor context.
 */
@Suppress("HardCodedStringLiteral")
open class ActionCompletionCommand(
  @field:Language("devkit-action-id") var actionId: String,
  presentableActionName: @Nls String,
  private val previewText: @Nls String?,
  override val icon: Icon? = null,
  override val priority: Int? = null,
  override val highlightInfo: HighlightInfoLookup? = null,
  override val synonyms: List<String> = emptyList()
) : CompletionCommand(), DumbAware {

  override val presentableName: @Nls String = presentableActionName
    .replaceFirst("_", "")
    .lowercase()
    .replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }

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
    //drop data context caches because it can be cached before psi was changed and it is necessary to refresh
    ActivityTracker.getInstance().inc()
    val dataContext = DataManager.getInstance().getDataContext(editor.getComponent())
    val presentation: Presentation = action.templatePresentation.clone()
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION,
                                          ActionUiKind.NONE, null)
    ActionUtil.performAction(action, event)
  }

  override fun getPreview(): IntentionPreviewInfo {
    if (previewText == null) return IntentionPreviewInfo.EMPTY
    return IntentionPreviewInfo.Html(previewText)
  }
}