// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.SystemProperties;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collections;

public final class FileTypeIndexImpl
        extends ScalarIndexExtension<FileType>
        implements CustomImplementationFileBasedIndexExtension<FileType, Void> {
  private static final boolean USE_LOG_INDEX = SystemProperties.getBooleanProperty("use.log.file.type.index", true);
  @NotNull
  @Override
  public ID<FileType, Void> getName() {
    return FileTypeIndex.NAME;
  }

  @NotNull
  @Override
  public DataIndexer<FileType, Void, FileContent> getIndexer() {
    if (USE_LOG_INDEX) {
      throw new UnsupportedOperationException();
    }
    return in -> Collections.singletonMap(in.getFileType(), null);
  }

  @NotNull
  @Override
  public KeyDescriptor<FileType> getKeyDescriptor() {
    return new FileTypeKeyDescriptor();
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return file -> !file.isDirectory();
  }

  @Override
  public boolean dependsOnFileContent() {
    return false;
  }

  @Override
  public int getVersion() {
    return 3 + (USE_LOG_INDEX ? 0xFF : 0);
  }

  @Override
  public @NotNull UpdatableIndex<FileType, Void, FileContent, ?> createIndexImplementation(@NotNull FileBasedIndexExtension<FileType, Void> extension,
                                                                                           @NotNull VfsAwareIndexStorageLayout<FileType, Void> indexStorageLayout)
    throws StorageException, IOException {
    return USE_LOG_INDEX ? new LogFileTypeIndex(extension) : new FileTypeMapReduceIndex(extension, indexStorageLayout);
  }
}
