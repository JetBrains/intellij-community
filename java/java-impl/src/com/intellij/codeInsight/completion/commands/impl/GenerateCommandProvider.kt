// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.CompletionCommand
import com.intellij.codeInsight.completion.commands.api.CommandProvider
import com.intellij.codeInsight.generation.actions.BaseGenerateAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile


class GenerateCommandProvider : CommandProvider {
  override fun getCommands(project: Project,
                           editor: Editor,
                           offset: Int,
                           psiFile: PsiFile,
                           originalEditor: Editor,
                           originalOffset: Int,
                           originalFile: PsiFile): List<CompletionCommand> {
    val element = psiFile.findElementAt(offset)
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.EDITOR, editor)
      .add(CommonDataKeys.PSI_ELEMENT, element)
      .build()
    val actionEvent = AnActionEvent.createEvent(dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
    actionEvent.updateSession = UpdateSession.EMPTY
    Utils.initUpdateSession(actionEvent)

    val generateActions: MutableList<BaseGenerateAction> = ArrayList()
    if (element == null || element.parent !is PsiClass) return emptyList()
    for (action in ActionGroupUtil.getActiveActions(ActionManager.getInstance().getAction(IdeActions.GROUP_GENERATE) as ActionGroup, actionEvent)) {
      if (action is BaseGenerateAction) {
        generateActions.add(action)
      }
    }
    return generateActions.map { GenerateCompletionCommand(it) }
  }

  override fun getId(): String {
    return "GenerateCommandProvider"
  }
}