// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.CompletionCommand
import com.intellij.codeInsight.generation.actions.BaseGenerateAction
import com.intellij.ide.DataManager
import com.intellij.java.JavaBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import javax.swing.Icon


class GenerateCompletionCommand(private val action: BaseGenerateAction) : CompletionCommand() {
  override val name: String
    get() = "Generate \'" + action.templateText + "\'"
  override val i18nName: @Nls String
    get() = JavaBundle.message(
      "command.completion.generate.text",
      action.templateText)
  override val icon: Icon?
    get() = null

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    if (editor == null) return
    val dataContext = DataManager.getInstance().getDataContext(editor.getComponent())
    val presentation: Presentation = action.templatePresentation.clone()
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event)
    }
  }
}