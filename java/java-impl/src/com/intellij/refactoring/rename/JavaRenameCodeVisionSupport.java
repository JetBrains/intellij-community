// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;

public class JavaRenameCodeVisionSupport extends RenameCodeVisionSupport {
  @Override
  public boolean supports(@NotNull FileType fileType) {
    return fileType == JavaFileType.INSTANCE;
  }
}
