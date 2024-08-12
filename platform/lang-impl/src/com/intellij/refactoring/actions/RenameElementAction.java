// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SyntheticElement;
import com.intellij.refactoring.InplaceRefactoringContinuation;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.Renamer;
import com.intellij.refactoring.rename.RenamerFactory;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RenameElementAction extends AnAction {

  public RenameElementAction() {
    setInjectedContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isAvailable(e.getDataContext()));
  }

  @ApiStatus.Internal
  public boolean isAvailable(@NotNull DataContext dataContext) {
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return false;
    }
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    if (editor != null && InplaceRefactoringContinuation.hasInplaceContinuation(editor, RenameElementAction.class)) {
      return true;
    }
    return getAvailableRenamers(dataContext).findAny().isPresent();
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    if (editor != null && InplaceRefactoringContinuation.tryResumeInplaceContinuation(project, editor, RenameElementAction.class)) {
      return;
    }

    if (!PsiDocumentManager.getInstance(project).commitAllDocumentsUnderProgress()) {
      return;
    }

    List<Renamer> renamers;
    try (var ignored = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
      renamers = getAvailableRenamers(dataContext).collect(Collectors.toList());
    }
    if (renamers.isEmpty()) {
      String message = RefactoringBundle.getCannotRefactorMessage(
        RefactoringBundle.message("error.wrong.caret.position.symbol.to.refactor")
      );
      CommonRefactoringUtil.showErrorHint(
        project,
        e.getData(CommonDataKeys.EDITOR),
        message,
        RefactoringBundle.getCannotRefactorMessage(null),
        null
      );
    }
    else if (renamers.size() == 1) {
      renamers.get(0).performRename();
    }
    else {
      JBPopupFactory.getInstance()
        .createPopupChooserBuilder(renamers)
        .setTitle(RefactoringBundle.message("what.would.you.like.to.do"))
        .setRenderer(new RenamerRenderer())
        .setItemChosenCallback(Renamer::performRename)
        .createPopup()
        .showInBestPositionFor(dataContext);
    }
  }

  private static @NotNull Stream<Renamer> getAvailableRenamers(@NotNull DataContext dataContext) {
    return RenamerFactory.EP_NAME.getExtensionList().stream().flatMap(factory -> factory.createRenamers(dataContext).stream());
  }

  public static boolean isRenameEnabledOnElements(PsiElement @NotNull [] elements) {
    if (elements.length != 1) return false;

    PsiElement element = elements[0];
    return element instanceof PsiNamedElement &&
           !(element instanceof SyntheticElement) &&
           !PsiElementRenameHandler.isVetoed(element);
  }
}
