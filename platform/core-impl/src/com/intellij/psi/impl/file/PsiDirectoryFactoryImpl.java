// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.file;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public final class PsiDirectoryFactoryImpl extends PsiDirectoryFactory {
  private final Project myProject;

  public PsiDirectoryFactoryImpl(@NotNull Project project) {
    myProject = project;
  }
  @Override
  public @NotNull PsiDirectory createDirectory(@NotNull VirtualFile file) {
    return new PsiDirectoryImpl((PsiManagerImpl)PsiManager.getInstance(myProject), file);
  }

  @Override
  public @NotNull String getQualifiedName(@NotNull PsiDirectory directory, boolean presentable) {
    if (presentable) {
      return FileUtil.getLocationRelativeToUserHome(directory.getVirtualFile().getPresentableUrl());
    }
    return "";
  }

  @Override
  public PsiDirectoryContainer getDirectoryContainer(@NotNull PsiDirectory directory) {
    return null;
  }

  @Override
  public boolean isPackage(@NotNull PsiDirectory directory) {
    return false;
  }

  @Override
  public boolean isValidPackageName(String name) {
    return true;
  }
}
