// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.file;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class PsiDirectoryFactory {
  public static PsiDirectoryFactory getInstance(Project project) {
    return project.getService(PsiDirectoryFactory.class);
  }

  public abstract @NotNull PsiDirectory createDirectory(@NotNull VirtualFile file);

  public abstract @NotNull @NlsSafe String getQualifiedName(@NotNull PsiDirectory directory, boolean presentable);

  public abstract @Nullable PsiDirectoryContainer getDirectoryContainer(@NotNull PsiDirectory directory);

  public abstract boolean isPackage(@NotNull PsiDirectory directory);

  public abstract boolean isValidPackageName(@Nullable String name);
}
