// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SyntheticElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;


public class RenameJavaImplicitClassRenamer implements RenameHandler {
  private final RenameJavaImplicitClassProcessor myProcessor = new RenameJavaImplicitClassProcessor();

  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    PsiElement element = PsiElementRenameHandler.getElement(dataContext);
    if (element == null || element instanceof SyntheticElement || !element.isWritable()) {
      return false;
    }
    if (!myProcessor.canProcessElement(element)) {
      return false;
    }
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return false;
    }
    if (!PsiManager.getInstance(project).isInProject(element)) {
      return false;
    }
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    //do nothing, it is not expected to be call from editor
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    if (elements.length != 1) {
      return;
    }
    PsiElementRenameHandler.rename(elements[0], project, null, null, null, myProcessor);
  }

  @TestOnly
  public void invoke(@NotNull Project project, @NotNull PsiElement element, String defaultName) {
    PsiElementRenameHandler.rename(element, project, null, null, defaultName, myProcessor);
  }
}
