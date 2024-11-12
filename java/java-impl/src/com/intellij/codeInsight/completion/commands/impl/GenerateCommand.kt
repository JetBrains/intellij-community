// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.OldCommand
import com.intellij.codeInsight.generation.actions.BaseGenerateAction
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import javax.swing.Icon


class GenerateCommand(private val action: BaseGenerateAction) : OldCommand() {
  override val name: String
    get() = "Generate \"" + action.templateText + "\""
  override val icon: Icon?
    get() = null

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val context = psiFile.findElementAt(offset) ?: return false
    if (editor == null) return false
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, psiFile.project)
      .add(CommonDataKeys.EDITOR, editor)
      .add(CommonDataKeys.PSI_ELEMENT, context)
      .build()
    val presentation: Presentation = action.templatePresentation.clone()
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)

    action.update(event)

    return event.presentation.isEnabled
  }

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    if (editor == null) return
    val dataContext = DataManager.getInstance().getDataContext(editor.getComponent())
    val presentation: Presentation = action.templatePresentation.clone()
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event);
    }
  }
}