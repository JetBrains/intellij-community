// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

/**
 * @author yole
 */
public abstract class PsiNavigationSupport {
  public static PsiNavigationSupport getInstance() {
    return ApplicationManager.getApplication().getService(PsiNavigationSupport.class);
  }

  @Nullable
  public abstract Navigatable getDescriptor(@NotNull PsiElement element);

  @NotNull
  public abstract Navigatable createNavigatable(@NotNull Project project, @NotNull VirtualFile vFile, int offset);

  public abstract boolean canNavigate(@NotNull PsiElement element);
  public abstract void navigateToDirectory(@NotNull PsiDirectory psiDirectory, boolean requestFocus);
  public abstract void openDirectoryInSystemFileManager(@NotNull File file);
}
