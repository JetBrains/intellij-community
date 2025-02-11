// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.ApplicableCompletionCommand
import com.intellij.codeInsight.completion.command.getDataContext
import com.intellij.codeInsight.completion.command.getTargetContext
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
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

/**
 * Represents an abstract action completion command that triggers a specific IDE action
 * identified by its `actionId`. This class allows integration of IntelliJ Platform actions
 * as part of code completion features, ensuring that the action is applicable and
 * executable within a given code editor context.
 */
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
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION, ActionUiKind.Companion.NONE, null)
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
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION, ActionUiKind.Companion.NONE, null)
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