// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.ApplicableCompletionCommand
import com.intellij.codeInsight.completion.commands.api.getDataContext
import com.intellij.codeInsight.completion.commands.api.getTargetContext
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import javax.swing.Icon

abstract class AbstractActionCompletionCommand(
  var actionId: String,
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
    if (!DumbService.getInstance(psiFile.project).isUsableInCurrentContext(action)) return false
    val context = getTargetContext(offset, editor)
    val dataContext = getDataContext(psiFile, editor, context)
    val presentation: Presentation = action.templatePresentation.clone()
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION, ActionUiKind.NONE, null)
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
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION, ActionUiKind.NONE, null)
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event)
    }
  }

  companion object {
    internal fun isApplicableToProject(offset: Int, psiFile: PsiFile): Boolean {
      if (offset - 1 < 0) return true
      val element = psiFile.findElementAt(offset - 1)
      if (element is PsiComment) return true
      val ch = psiFile.fileDocument.immutableCharSequence[offset - 1]
      if (!ch.isLetterOrDigit() && ch != ']' && ch != ')') return true
      return false
    }
  }
}