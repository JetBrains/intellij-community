// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public final class FileTypeSpecificSubIndexer<SubIndexerType> {
  @NotNull
  private final SubIndexerType mySubIndexerType;
  @NotNull
  private final FileType myFileType;

  public FileTypeSpecificSubIndexer(@NotNull SubIndexerType type, @NotNull FileType fileType) {
    mySubIndexerType = type;
    myFileType = fileType;
  }

  @NotNull
  public SubIndexerType getSubIndexerType() {
    return mySubIndexerType;
  }

  @NotNull
  public FileType getFileType() {
    return myFileType;
  }
}