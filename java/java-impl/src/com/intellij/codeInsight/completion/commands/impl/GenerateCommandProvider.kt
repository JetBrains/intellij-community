// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.*
import com.intellij.codeInsight.generation.actions.BaseGenerateAction
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiIdentifier
import com.intellij.util.containers.JBIterable.from


class GenerateCommandProvider : CommandProvider, DumbAware {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    val psiFile = context.psiFile
    val project = context.project
    val offset = context.offset
    val editor = context.editor
    if (InjectedLanguageManager.getInstance(psiFile.project).isInjectedFragment(psiFile)) return emptyList()
    val element = psiFile.findElementAt(if (offset - 1 >= 0) offset - 1 else offset)
    val parent = element?.parent
    //skip, probably extension to skip?
    if (element == null || parent !is PsiClass) return emptyList()
    if (!isOnlySpaceInLine(psiFile.fileDocument, offset) &&
        !(element is PsiIdentifier && parent.nameIdentifier == element)) return emptyList()
    val context = getTargetContext(offset, editor)
    val dataContext = getDataContext(psiFile, editor, context)
    val actionEvent = AnActionEvent.createEvent(dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
    actionEvent.updateSession = UpdateSession.EMPTY
    Utils.initUpdateSession(actionEvent)
    val generateActions: MutableList<BaseGenerateAction> = ArrayList()
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

  private fun isOnlySpaceInLine(document: Document, offset: Int): Boolean {
    val lineNumber = document.getLineNumber(offset)
    val lineStartOffset = document.getLineStartOffset(lineNumber)
    val immutableCharSequence = document.immutableCharSequence
    for (i in lineStartOffset..offset) {
      if (!immutableCharSequence[i].isWhitespace()) {
        return false
      }
    }
    return true
  }

  override fun getId(): String {
    return "GenerateCommandProvider"
  }
}