// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.find.actions.ShowUsagesAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.navigation.actions.GotoDeclarationAction.findElementToShowUsagesOf;

public final class GotoDeclarationActionHandler implements CodeInsightActionHandler {

  public static final GotoDeclarationActionHandler INSTANCE = new GotoDeclarationActionHandler();

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    final PsiElement declarationElement = findDeclarationElement(project, editor);
    if (declarationElement == null) {
      GotoDeclarationOnlyHandler.INSTANCE.invoke(project, editor, file);
    }
    else {
      startFindUsages(editor, project, declarationElement);
    }
  }

  @Nullable
  private static PsiElement findDeclarationElement(@NotNull Project project, @NotNull Editor editor) {
    final DumbService dumbService = DumbService.getInstance(project);
    try {
      return dumbService.computeWithAlternativeResolveEnabled(() -> {
        int offset = editor.getCaretModel().getOffset();
        return findElementToShowUsagesOf(editor, offset);
      });
    }
    catch (IndexNotReadyException e) {
      dumbService.showDumbModeNotification("Navigation is not available here during index update");
      return null;
    }
  }

  private static void startFindUsages(@NotNull Editor editor, @NotNull Project project, @NotNull PsiElement element) {
    if (DumbService.getInstance(project).isDumb()) {
      AnAction action = ActionManager.getInstance().getAction(ShowUsagesAction.ID);
      String name = action.getTemplatePresentation().getText();
      DumbService.getInstance(project).showDumbModeNotification(ActionUtil.getUnavailableMessage(name, false));
    }
    else {
      RelativePoint popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(editor);
      new ShowUsagesAction().startFindUsages(element, popupPosition, editor, ShowUsagesAction.getUsagesPageSize());
    }
  }
}
