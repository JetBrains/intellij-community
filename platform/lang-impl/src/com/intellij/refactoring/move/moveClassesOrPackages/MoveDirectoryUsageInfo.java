// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.psi.PsiDirectory;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MoveDirectoryUsageInfo extends UsageInfo {
  private final PsiDirectory myTargetDirectory;

  public MoveDirectoryUsageInfo(@NotNull PsiDirectory sourceDirectory, @NotNull PsiDirectory targetDirectory) {
    super(sourceDirectory);
    myTargetDirectory = targetDirectory;
  }

  @NotNull
  public PsiDirectory getSourceDirectory() {
    return (PsiDirectory)Objects.requireNonNull(getElement());
  }

  @NotNull
  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }
}
