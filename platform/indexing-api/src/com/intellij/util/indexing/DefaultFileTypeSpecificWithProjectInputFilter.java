// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public class DefaultFileTypeSpecificWithProjectInputFilter implements FileBasedIndex.FileTypeSpecificInputFilter,
                                                                      FileBasedIndex.ProjectSpecificInputFilter {
  private final FileType[] myFileTypes;

  public DefaultFileTypeSpecificWithProjectInputFilter(FileType @NotNull ... fileTypes) {
    myFileTypes = fileTypes;
  }

  @Override
  public boolean acceptInput(@NotNull IndexedFile file) {
    return true;
  }

  @Override
  public void registerFileTypesUsedForIndexing(@NotNull Consumer<? super FileType> fileTypeSink) {
    for (FileType ft : myFileTypes) {
      fileTypeSink.consume(ft);
    }
  }
}
