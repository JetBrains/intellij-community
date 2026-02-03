// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

@Experimental
@Internal
public final class FileTypeSpecificSubIndexer<SubIndexerType> {
  private final @NotNull SubIndexerType mySubIndexerType;
  private final @NotNull FileType myFileType;

  public FileTypeSpecificSubIndexer(@NotNull SubIndexerType type, @NotNull FileType fileType) {
    mySubIndexerType = type;
    myFileType = fileType;
  }

  public @NotNull SubIndexerType getSubIndexerType() {
    return mySubIndexerType;
  }

  public @NotNull FileType getFileType() {
    return myFileType;
  }
}