package com.intellij.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class RenameDialogFactoryImpl implements RenameDialogFactory {

  @Override
  @NotNull
  public RenameDialogViewModel createRenameDialog(@NotNull Project project,
                                                  @NotNull PsiElement substituted,
                                                  @NotNull PsiElement nameSuggestionContext,
                                                  @NotNull Editor editor,
                                                  @NotNull RenamePsiElementProcessor processor) {
    return processor.createRenameDialog(project, substituted, nameSuggestionContext, editor);
  }
}
