// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.intention.actions;

import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import com.intellij.icons.AllIcons;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

public final class ShowIntentionActionsAction extends BaseCodeInsightAction implements HintManagerImpl.ActionToIgnore,
                                                                                       LightEditCompatible,
                                                                                       DumbAware,
                                                                                       ActionRemoteBehaviorSpecification.Frontend {
  public ShowIntentionActionsAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    Presentation presentation = event.getPresentation();
    if (LightEdit.owns(project)) {
      presentation.setEnabledAndVisible(!ActionPlaces.EDITOR_POPUP.equals(event.getPlace()));
      return;
    }
    super.update(event);
    boolean isInFloatingToolbar = ActionPlaces.EDITOR_FLOATING_TOOLBAR.equals(event.getPlace());
    if (isInFloatingToolbar || ActionPlaces.EDITOR_HINT.equals(event.getPlace())) {
      presentation.setIcon(AllIcons.Actions.IntentionBulb);
    }
    if (isInFloatingToolbar) {
      presentation.setPopupGroup(true);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    if (!LightEdit.owns(project) && DumbService.isDumb(project)) {
      DumbService.getInstance(project).showDumbModeNotificationForAction(
        ApplicationBundle.message("intentions.are.not.available.message"),
        ActionManager.getInstance().getId(this));
      return;
    }

    Editor editor = getEditor(e.getDataContext(), project, false);
    if (editor == null) return;

    PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (psiFile == null) return;

    if (!ApplicationManager.getApplication().isUnitTestMode() && !editor.getContentComponent().isShowing()) return;
    getHandler().invoke(project, editor, psiFile, e.isFromContextMenu());
  }

  @Override
  protected boolean isValidForLookup() {
    return true;
  }

  @Override
  protected @NotNull ShowIntentionActionsHandler getHandler() {
    return new ShowIntentionActionsHandler();
  }
}
