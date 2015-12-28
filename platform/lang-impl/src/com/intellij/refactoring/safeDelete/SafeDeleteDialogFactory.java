package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface SafeDeleteDialogFactory {

  @NotNull
  SafeDeleteDialog createDialog(@NotNull Project project,
                                @NotNull PsiElement[] elements,
                                @NotNull SafeDeleteDialog.Callback callback,
                                boolean isDelete);

  class SERVICE {
    public static SafeDeleteDialogFactory getInstance() {
      return ServiceManager.getService(SafeDeleteDialogFactory.class);
    }
  }
}
