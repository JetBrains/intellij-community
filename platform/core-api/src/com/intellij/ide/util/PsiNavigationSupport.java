// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class PsiNavigationSupport {
  public static PsiNavigationSupport getInstance() {
    return ApplicationManager.getApplication().getService(PsiNavigationSupport.class);
  }

  public abstract @Nullable Navigatable getDescriptor(@NotNull PsiElement element);

  public abstract @NotNull Navigatable createNavigatable(@NotNull Project project, @NotNull VirtualFile vFile, int offset);

  public abstract boolean canNavigate(@NotNull PsiElement element);

  public abstract void navigateToDirectory(@NotNull PsiDirectory psiDirectory, boolean requestFocus);

  public abstract void openDirectoryInSystemFileManager(@NotNull File file);
}
