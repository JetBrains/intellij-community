// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class CreateGroupHandler implements InputValidatorEx {

  protected final @NotNull Project myProject;
  protected final @NotNull PsiDirectory myDirectory;

  protected @Nullable PsiFileSystemItem createdElement;
  protected @Nullable @NlsContexts.DetailedDescription String errorText;

  CreateGroupHandler(@NotNull Project project, @NotNull PsiDirectory directory) {
    myProject = project;
    myDirectory = directory;
  }

  @Override
  public @Nullable String getErrorText(String inputString) {
    return errorText;
  }

  @Nullable
  PsiFileSystemItem getCreatedElement() {
    return createdElement;
  }

  abstract @NotNull String getInitialText();
}
