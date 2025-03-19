// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.testIntegration;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

public class GotoTestOrCodeAction extends BaseCodeInsightAction {
  @Override
  protected @NotNull CodeInsightActionHandler getHandler(){
    return new GotoTestOrCodeHandler();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(false);
    if (TestFinderHelper.getFinders().isEmpty()) {
      return;
    }

    Project project = e.getProject();
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null || project == null) return;

    PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (psiFile == null) return;

    PsiElement element = GotoTestOrCodeHandler.getSelectedElement(editor, psiFile);

    if (TestFinderHelper.findSourceElement(element) == null) return;

    presentation.setEnabledAndVisible(true);
    boolean useShortName = e.isFromMainMenu() || e.isFromContextMenu();
    if (TestFinderHelper.isTest(element)) {
      presentation.setText(useShortName ? ActionsBundle.messagePointer("action.GotoTestSubject.MainMenu.text") : ActionsBundle.messagePointer("action.GotoTestSubject.text"));
      presentation.setDescription(ActionsBundle.messagePointer("action.GotoTestSubject.description"));
    } else {
      presentation.setText(useShortName ? ActionsBundle.messagePointer("action.GotoTest.MainMenu.text") : ActionsBundle.messagePointer("action.GotoTest.text"));
      presentation.setDescription(ActionsBundle.messagePointer("action.GotoTest.description"));
    }
  }
}
