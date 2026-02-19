// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 */
public final class PsiPackageRenameValidator implements RenameInputValidatorEx {
  private final ElementPattern<? extends PsiElement> myPattern = PlatformPatterns.psiElement(PsiPackage.class);

  @Override
  public @NotNull ElementPattern<? extends PsiElement> getPattern() {
    return myPattern;
  }

  @Override
  public @Nullable String getErrorMessage(@NotNull String newName, @NotNull Project project) {
    if (FileTypeManager.getInstance().isFileIgnored(newName)) {
      return JavaBundle.message("rename.package.ignored.name.warning");
    }

    if (!newName.isEmpty()) {
      if (!PsiDirectoryFactory.getInstance(project).isValidPackageName(newName)) {
        return JavaBundle.message("rename.package.invalid.name.error");
      }
    }

    return null;
  }

  @Override
  public boolean isInputValid(@NotNull String newName, @NotNull PsiElement element, @NotNull ProcessingContext context) {
    return !newName.isEmpty();
  }
}