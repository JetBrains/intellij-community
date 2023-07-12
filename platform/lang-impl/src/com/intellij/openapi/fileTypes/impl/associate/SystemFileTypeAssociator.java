// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl.associate;

import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface SystemFileTypeAssociator {
  void associateFileTypes(@NotNull List<FileType> fileTypes) throws OSFileAssociationException;

  default boolean isOsRestartRequired() {
    return false;
  }
}
