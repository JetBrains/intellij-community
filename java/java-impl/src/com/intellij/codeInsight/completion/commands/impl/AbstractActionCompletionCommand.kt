// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.completion.commands.api.OldCompletionCommand
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

abstract class AbstractActionCompletionCommand(
  var actionId: String,
  override val name: String,
  override val icon: Icon?,
) : OldCompletionCommand() {
  private val action: AnAction? = ActionManager.getInstance().getAction(actionId)

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val action = action ?: return false
    if (editor == null) return false
    val context = getTargetContext(offset, editor)
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, psiFile.project)
      .add(CommonDataKeys.EDITOR, editor)
      .add(CommonDataKeys.PSI_ELEMENT, context)
      .add(CommonDataKeys.PSI_FILE, psiFile)
      .add(LangDataKeys.CONTEXT_LANGUAGES, arrayOf(psiFile.language))
      .build()
    val presentation: Presentation = action.templatePresentation.clone()
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION, ActionUiKind.NONE, null)

    action.update(event)

    return event.presentation.isEnabled && event.presentation.isVisible
  }

  private fun getTargetContext(offset: Int, editor: Editor): PsiElement? {
    try {
      val util = TargetElementUtil.getInstance()
      return util.findTargetElement(editor, util.getReferenceSearchFlags(), offset)
    }
    catch (e: IndexNotReadyException) {
      return null;
    }
  }

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    val action = action ?: return
    if (editor == null) return
    val dataContext = DataManager.getInstance().getDataContext(editor.getComponent())
    val presentation: Presentation = action.templatePresentation.clone()
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION, ActionUiKind.NONE, null)
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event);
    }
  }
}