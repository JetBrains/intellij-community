// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

public class DefaultFileTypeSpecificInputFilter implements FileBasedIndex.FileTypeSpecificInputFilter {
  private final FileType[] myFileTypes;

  public DefaultFileTypeSpecificInputFilter(@NotNull FileType... fileTypes) {
    myFileTypes = fileTypes;
  }

  @Override
  public void registerFileTypesUsedForIndexing(@NotNull Consumer<FileType> fileTypeSink) {
    for (FileType ft : myFileTypes) {
      fileTypeSink.consume(ft);
    }
  }

  @Override
  public boolean acceptInput(@NotNull VirtualFile file) {
    return true;
  }
}
