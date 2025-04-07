// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ui.StringValidatorWithSwingSelector;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.refactoring.util.CommonMoveClassesOrPackagesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class TargetPackageValidator implements StringValidatorWithSwingSelector {
  @NotNull private final String myPackageName;
  @Nullable private final PsiDirectory myBaseDirectory;

  public TargetPackageValidator(@NotNull String packageName, @Nullable PsiDirectory baseDir) {
    myPackageName = packageName;
    myBaseDirectory = baseDir;
  }
  
  @Override
  public @Nullable String select(@NotNull Project project) {
    PsiDirectory directory = CommonMoveClassesOrPackagesUtil.chooseDestinationPackage(
      project, myPackageName, myBaseDirectory);
    if (directory == null) return null;
    return directory.getVirtualFile().getPath();
  }

  @Override
  public @NotNull String validatorId() {
    return "java.package.target";
  }

  @Override
  public @Nullable String getErrorMessage(@Nullable Project project, @NotNull String path) {
    Path file;
    try {
      file = Path.of(path);
    }
    catch (InvalidPathException e) {
      return e.getMessage();
    }
    VirtualFile virtualFile = VfsUtil.findFile(file, true);
    if (virtualFile == null) return JavaBundle.message("validator.text.directory.not.found");
    if (!virtualFile.isDirectory()) return JavaBundle.message("validator.text.not.directory");
    // TODO: check that it points to a valid location inside a source root
    return null;
  }
}
