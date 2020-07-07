// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.actions;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SyntheticElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.Renamer;
import com.intellij.refactoring.rename.RenamerFactory;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.navigation.ChooserKt.chooseTargetPopup;

public class RenameElementAction extends AnAction implements UpdateInBackground {

  public RenameElementAction() {
    setInjectedContext(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isAvailable(e.getDataContext()));
  }

  @ApiStatus.Internal
  public boolean isAvailable(@NotNull DataContext dataContext) {
    return dataContext.getData(CommonDataKeys.PROJECT) != null
           && getAvailableRenamers(dataContext).findAny().isPresent();
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }

    int eventCount = IdeEventQueue.getInstance().getEventCount();
    if (!PsiDocumentManager.getInstance(project).commitAllDocumentsUnderProgress()) {
      return;
    }

    List<Renamer> renamers = getAvailableRenamers(dataContext).collect(Collectors.toList());
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
      chooseTargetPopup(
        RefactoringBundle.message("what.would.you.like.to.do"),
        renamers,
        renamer -> renamer::getPresentableText,
        Renamer::performRename
      ).showInBestPositionFor(dataContext);
    }
  }

  @NotNull
  private static Stream<Renamer> getAvailableRenamers(@NotNull DataContext dataContext) {
    return RenamerFactory.EP_NAME.extensions().flatMap(factory -> factory.createRenamers(dataContext).stream());
  }

  /**
   * @deprecated no longer used since RenameElementAction doesn't extend BaseRefactoringAction anymore; use {@code false}
   */
  @SuppressWarnings("MethodMayBeStatic")
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  @Deprecated
  public final boolean isAvailableInEditorOnly() {
    return false;
  }

  /**
   * @deprecated no longer used since RenameElementAction doesn't extend BaseRefactoringAction anymore; use {@link #isRenameEnabledOnElements}
   */
  @SuppressWarnings("MethodMayBeStatic")
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  @Deprecated
  public final boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    return isRenameEnabledOnElements(elements);
  }

  public static boolean isRenameEnabledOnElements(PsiElement @NotNull [] elements) {
    if (elements.length != 1) return false;

    PsiElement element = elements[0];
    return element instanceof PsiNamedElement &&
           !(element instanceof SyntheticElement) &&
           !PsiElementRenameHandler.isVetoed(element);
  }
}
