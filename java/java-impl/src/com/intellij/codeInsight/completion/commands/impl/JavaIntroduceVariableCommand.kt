// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.ApplicableCompletionCommand
import com.intellij.codeInsight.completion.command.getDataContext
import com.intellij.codeInsight.completion.command.getTargetContext
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
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

internal class JavaIntroduceVariableCommand : ApplicableCompletionCommand() {
  override val name: String
    get() = "Introduce variable"

  override val i18nName: @Nls String
    get() = RefactoringBundle.message("introduce.variable.title")

  override val icon: Icon
    get() = AllIcons.Nodes.Variable

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (editor == null) return false
    val factory = JavaRefactoringActionHandlerFactory.getInstance()
    val variableHandler = factory.createIntroduceVariableHandler()
    if (variableHandler is ContextAwareActionHandler) {
      return variableHandler.isAvailableForQuickList(editor, psiFile, getDataContext(psiFile, editor, getTargetContext(offset, editor)))
    }
    return false
  }

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
}