// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes target directory for quick fixes that create new files.
 *
 * @see CreateDirectoryPathFix
 * @see CreateFilePathFix
 */
public final class TargetDirectory {
  private final SmartPsiElementPointer<PsiDirectory> myDirectory;
  private final String @NotNull [] myPathToCreate;

  public TargetDirectory(@NotNull PsiDirectory directory) {
    this(directory, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public TargetDirectory(@NotNull PsiDirectory directory,
                         String @NotNull [] pathToCreate) {
    myDirectory = SmartPointerManager.getInstance(directory.getProject()).createSmartPsiElementPointer(directory);
    myPathToCreate = pathToCreate;
  }

  public String[] getPathToCreate() {
    return myPathToCreate;
  }

  public @Nullable PsiDirectory getDirectory() {
    return myDirectory.getElement();
  }
}
