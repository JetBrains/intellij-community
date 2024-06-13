// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.refactoring.RefactoringCodeVisionSupport;
import org.jetbrains.annotations.NotNull;

public class JavaRefactoringCodeVisionSupport extends RefactoringCodeVisionSupport {
  @Override
  public boolean supportsRename(@NotNull FileType fileType) {
    return fileType == JavaFileType.INSTANCE;
  }

  @Override
  public boolean supportsChangeSignature(@NotNull FileType fileType) {
    return fileType == JavaFileType.INSTANCE;
  }
}
