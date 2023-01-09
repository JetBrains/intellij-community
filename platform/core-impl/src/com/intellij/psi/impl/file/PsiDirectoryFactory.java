// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

  @NotNull
  public abstract PsiDirectory createDirectory(@NotNull VirtualFile file);

  @NotNull
  public abstract @NlsSafe String getQualifiedName(@NotNull PsiDirectory directory, boolean presentable);

  @Nullable
  public abstract PsiDirectoryContainer getDirectoryContainer(@NotNull PsiDirectory directory);

  public abstract boolean isPackage(@NotNull PsiDirectory directory);

  public abstract boolean isValidPackageName(@Nullable String name);
}
