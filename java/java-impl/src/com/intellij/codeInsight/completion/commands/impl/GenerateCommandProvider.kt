// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.CommandProvider
import com.intellij.codeInsight.completion.commands.api.CompletionCommand
import com.intellij.codeInsight.completion.commands.api.getDataContext
import com.intellij.codeInsight.completion.commands.api.getTargetContext
import com.intellij.codeInsight.generation.actions.BaseGenerateAction
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.util.containers.JBIterable.from


class GenerateCommandProvider : CommandProvider, DumbAware {
  override fun getCommands(
    project: Project,
    editor: Editor,
    offset: Int,
    psiFile: PsiFile,
    originalEditor: Editor,
    originalOffset: Int,
    originalFile: PsiFile,
    isNonWritten: Boolean,
  ): List<CompletionCommand> {
    if (InjectedLanguageManager.getInstance(psiFile.project).isInjectedFragment(psiFile)) return emptyList()
    val element = psiFile.findElementAt(offset)
    val context = getTargetContext(offset, editor)
    val dataContext = getDataContext(psiFile, editor, context)
    val actionEvent = AnActionEvent.createEvent(dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
    actionEvent.updateSession = UpdateSession.EMPTY
    Utils.initUpdateSession(actionEvent)

    val generateActions: MutableList<BaseGenerateAction> = ArrayList()
    if (element == null || element.parent !is PsiClass) return emptyList()
    val session = actionEvent.updateSession
    val dumbService = DumbService.getInstance(project)
    val activeActions = from(session.expandedChildren(ActionManager.getInstance().getAction(IdeActions.GROUP_GENERATE) as ActionGroup))
      .filter { dumbService.isUsableInCurrentContext(it) }
      .filter { o: AnAction? -> o !is Separator && o != null && session.presentation(o).isEnabledAndVisible }
    for (action in activeActions) {
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