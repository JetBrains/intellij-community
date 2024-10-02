// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiTodoSearchHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CustomChangelistTodoTreeStructure extends TodoTreeStructure {
  private final PsiTodoSearchHelper myCustomSearchHelper;

  public CustomChangelistTodoTreeStructure(Project project, PsiTodoSearchHelper customSearchHelper) {
    super(project);
    myCustomSearchHelper = customSearchHelper;
  }

  @Override
  public boolean accept(final @NotNull PsiFile psiFile) {
    if (!psiFile.isValid()) return false;
    return getSearchHelper().getTodoItemsCount(psiFile) > 0;
  }

  @Override
  public PsiTodoSearchHelper getSearchHelper() {
    return myCustomSearchHelper;
  }
}
