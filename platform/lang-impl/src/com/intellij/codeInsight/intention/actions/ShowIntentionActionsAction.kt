// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.actions

import com.intellij.codeInsight.actions.BaseCodeInsightAction
import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.icons.AllIcons
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification.FrontendThenBackend
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.util.PsiUtilBase

internal class ShowIntentionActionsAction : BaseCodeInsightAction(), ActionToIgnore, LightEditCompatible, DumbAware, FrontendThenBackend {
  init {
    isEnabledInModalContext = true
  }

  override fun update(event: AnActionEvent) {
    val project = event.project
    val presentation = event.presentation
    if (LightEdit.owns(project)) {
      presentation.setEnabledAndVisible(ActionPlaces.EDITOR_POPUP != event.place)
      return
    }

    super.update(event)

    val isInFloatingToolbar = ActionPlaces.EDITOR_FLOATING_TOOLBAR == event.place
    if (isInFloatingToolbar || ActionPlaces.EDITOR_HINT == event.place) {
      presentation.setIcon(AllIcons.Actions.IntentionBulb)
    }
    if (isInFloatingToolbar) {
      presentation.isPopupGroup = true
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = getEditor(e.dataContext, project, false) ?: return

    if (!ApplicationManager.getApplication().isUnitTestMode() && !editor.getContentComponent().isShowing()) {
      return
    }

    val psiFile = PsiUtilBase.getPsiFileInEditor(editor, project) ?: return
    getHandler(e.dataContext).invoke(project, editor, psiFile, e.isFromContextMenu)
  }

  override fun isValidForLookup(): Boolean = true

  override fun getHandler(): ShowIntentionActionsHandler {
    return ShowIntentionActionsHandler()
  }

  override fun getHandler(dataContext: DataContext): ShowIntentionActionsHandler {
    return ShowIntentionActionsHandler()
  }
}
