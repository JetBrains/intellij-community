// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Stream;

public class RenameElementAction extends DumbAwareAction {

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
    WriteIntentReadAction.run( () -> {
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

      List<Renamer> allRenamers = Utils.computeWithProgressIcon(e.getDataContext(), e.getPlace(), __ -> ReadAction.compute(
        () -> getAvailableRenamers(dataContext).toList()));

      List<Renamer> availableRenamers = ContainerUtil.filter(allRenamers, DumbService.getInstance(project)::isUsableInCurrentContext);


      if (availableRenamers.isEmpty()) {
        // check if rename is not performed due to dumb mode
        if (!allRenamers.isEmpty() && DumbService.isDumb(project)) {
          String actionUnavailableMessage = IdeBundle.message("dumb.balloon.0.is.not.available.while.indexing", this.getTemplateText());
          Runnable rerunAction = () -> {
            ActionManager.getInstance().tryToExecute(this, null, null, null, true);
          };
          String id = ActionManager.getInstance().getId(this);
          List<String> actionIds = id != null ? List.of(id) : List.of();
          DumbService.getInstance(project).showDumbModeActionBalloon(actionUnavailableMessage, rerunAction, actionIds);
          return;
        }

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
      else if (availableRenamers.size() == 1) {
        availableRenamers.get(0).performRename();
      }
      else {
        JBPopupFactory.getInstance()
          .createPopupChooserBuilder(availableRenamers)
          .setTitle(RefactoringBundle.message("what.would.you.like.to.do"))
          .setRenderer(new RenamerRenderer())
          .setItemChosenCallback(Renamer::performRename)
          .createPopup()
          .showInBestPositionFor(dataContext);
      }
    });
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
