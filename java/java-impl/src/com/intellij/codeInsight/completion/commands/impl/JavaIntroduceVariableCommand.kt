// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CommandProvider
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.CompletionCommandWithPreview
import com.intellij.codeInsight.completion.command.getDataContext
import com.intellij.codeInsight.completion.command.getTargetContext
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.idea.ActionsBundle
import com.intellij.lang.ContextAwareActionHandler
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.actions.IntroduceVariableAction
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class JavaIntroduceVariableCommandProvider : CommandProvider {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    val editor = context.editor
    val psiFile = context.psiFile
    val offset = context.offset
    val factory = JavaRefactoringActionHandlerFactory.getInstance()
    val variableHandler = factory.createIntroduceVariableHandler()
    if (variableHandler is ContextAwareActionHandler &&
        variableHandler.isAvailableForQuickList(editor, psiFile, getDataContext(psiFile, editor, getTargetContext(offset, editor)))) {
      return listOf(createCommand())
    }
    return emptyList()
  }

  private fun createCommand(): CompletionCommand {
    return JavaIntroduceVariableCommand()
  }
}

internal class JavaIntroduceVariableCommand : CompletionCommand(), CompletionCommandWithPreview {
  override val name: String
    get() = "Introduce variable"

  override val i18nName: @Nls String
    get() = RefactoringBundle.message("introduce.variable.title")

  override val icon: Icon
    get() = AllIcons.Nodes.Variable

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    val action = IntroduceVariableAction()
    if (editor == null) return
    val dataContext = DataManager.getInstance().getDataContext(editor.getComponent())
    val presentation: Presentation = action.templatePresentation.clone()
    val event = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION, ActionUiKind.Companion.NONE, null)

    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event)
    }
  }

  override fun getPreview(): IntentionPreviewInfo? {
    return IntentionPreviewInfo.Html(ActionsBundle.message("action.IntroduceVariable.description"))
  }
}