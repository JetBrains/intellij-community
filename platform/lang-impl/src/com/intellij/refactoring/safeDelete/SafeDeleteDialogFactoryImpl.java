package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class SafeDeleteDialogFactoryImpl implements SafeDeleteDialogFactory {
  @NotNull
  @Override
  public SafeDeleteDialog createDialog(@NotNull Project project, @NotNull PsiElement[] elements, @NotNull SafeDeleteDialog.Callback callback, boolean isDelete) {
    return new JSafeDeleteDialog(project, elements, callback, isDelete);
  }
}
