// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FilePackageMacro extends Macro {
  @Override
  public @NotNull String getName() {
    return "FilePackage";
  }

  @Override
  public @NotNull String getDescription() {
    return JavaBundle.message("macro.file.package");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    PsiPackage aPackage = getFilePackage(dataContext);
    if (aPackage == null) return null;
    return aPackage.getName();
  }

  static @Nullable PsiPackage getFilePackage(DataContext dataContext) {
    PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (psiFile == null) return null;
    PsiDirectory containingDirectory = psiFile.getContainingDirectory();
    if (containingDirectory == null || !containingDirectory.isValid()) return null;
    return JavaDirectoryService.getInstance().getPackage(containingDirectory);
  }
}
