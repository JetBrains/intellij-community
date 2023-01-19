// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * Filters according to given set of {@link FileType}s. Provided file types will be checked against substituted
 * file type if applicable - see {@link SubstitutedFileType#substituteFileType}
 * <p>
 * Override {@link #acceptInput(VirtualFile)} for additional filtering (e.g., file location, fixed filenames,
 * original file type).
 */
public class DefaultFileTypeSpecificInputFilter implements FileBasedIndex.FileTypeSpecificInputFilter {
  private final FileType[] myFileTypes;

  public DefaultFileTypeSpecificInputFilter(FileType @NotNull ... fileTypes) {
    myFileTypes = fileTypes;
  }

  @Override
  public void registerFileTypesUsedForIndexing(@NotNull Consumer<? super FileType> fileTypeSink) {
    for (FileType ft : myFileTypes) {
      fileTypeSink.consume(ft);
    }
  }

  @Override
  public boolean acceptInput(@NotNull VirtualFile file) {
    return true;
  }
}
