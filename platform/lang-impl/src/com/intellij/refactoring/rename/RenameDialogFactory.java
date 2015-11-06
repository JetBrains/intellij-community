package com.intellij.refactoring.rename;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface RenameDialogFactory {

  @NotNull
  RenameDialogViewModel createRenameDialog(Project project,
                                           @NotNull PsiElement substituted,
                                           @NotNull PsiElement nameSuggestionContext,
                                           @NotNull Editor editor,
                                           @NotNull RenamePsiElementProcessor processor);

  class SERVICE {
    public static RenameDialogFactory getInstance() {
      return ServiceManager.getService(RenameDialogFactory.class);
    }
  }
}
