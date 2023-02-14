// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.file;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import org.jetbrains.annotations.NotNull;


public final class PsiDirectoryFactoryImpl extends PsiDirectoryFactory {
  private final Project myProject;

  public PsiDirectoryFactoryImpl(@NotNull Project project) {
    myProject = project;
  }
  @NotNull
  @Override
  public PsiDirectory createDirectory(@NotNull VirtualFile file) {
    return new PsiDirectoryImpl((PsiManagerImpl)PsiManager.getInstance(myProject), file);
  }

  @Override
  @NotNull
  public String getQualifiedName(@NotNull PsiDirectory directory, boolean presentable) {
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
