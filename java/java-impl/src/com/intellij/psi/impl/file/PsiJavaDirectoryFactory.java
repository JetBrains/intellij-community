// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;


public final class PsiJavaDirectoryFactory extends PsiDirectoryFactory {
  private final PsiManagerEx myManager;

  public PsiJavaDirectoryFactory(@NotNull Project project) {
    myManager = PsiManagerEx.getInstanceEx(project);
  }

  @Override
  public @NotNull PsiDirectory createDirectory(final @NotNull VirtualFile file) {
    return new PsiJavaDirectoryImpl(myManager, file);
  }

  @Override
  public @NotNull String getQualifiedName(final @NotNull PsiDirectory directory, final boolean presentable) {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (aPackage != null) {
      final String qualifiedName = aPackage.getQualifiedName();
      if (!qualifiedName.isEmpty()) return qualifiedName;
      if (presentable) {
        return JavaBundle.message("default.package.presentable.name") + " (" + directory.getVirtualFile().getPresentableUrl() + ")";
      }
      return "";
    }
    return presentable ? StringUtil.notNullize(FileUtil.getLocationRelativeToUserHome(directory.getVirtualFile().getPresentableUrl())) : "";
  }

  @Override
  public @Nullable PsiDirectoryContainer getDirectoryContainer(@NotNull PsiDirectory directory) {
    return JavaDirectoryService.getInstance().getPackage(directory);
  }

  @Override
  public boolean isPackage(@NotNull PsiDirectory directory) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex();
    VirtualFile virtualFile = directory.getVirtualFile();
    PackageIndex packageIndex = PackageIndex.getInstance(myManager.getProject());
    return fileIndex.isUnderSourceRootOfType(virtualFile, JavaModuleSourceRootTypes.SOURCES) 
           && packageIndex.getPackageNameByDirectory(virtualFile) != null;
  }

  @Override
  public boolean isValidPackageName(String name) {
    return PsiNameHelper.getInstance(myManager.getProject()).isQualifiedName(name);
  }
}
