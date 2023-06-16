// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private static final boolean USE_LOG_INDEX = SystemProperties.getBooleanProperty("use.log.file.type.index", false);
  private static final boolean USE_MAPPED_INDEX = SystemProperties.getBooleanProperty("use.mapped.file.type.index", true);

  @Override
  public @NotNull ID<FileType, Void> getName() {
    return FileTypeIndex.NAME;
  }

  @Override
  public @NotNull DataIndexer<FileType, Void, FileContent> getIndexer() {
    if (USE_LOG_INDEX || USE_MAPPED_INDEX) {
      throw new UnsupportedOperationException();
    }
    return in -> Collections.singletonMap(in.getFileType(), null);
  }

  @Override
  public @NotNull KeyDescriptor<FileType> getKeyDescriptor() {
    return new FileTypeKeyDescriptor();
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return file -> !file.isDirectory();
  }

  @Override
  public boolean dependsOnFileContent() {
    return false;
  }

  @Override
  public int getVersion() {
    return USE_MAPPED_INDEX ? 0x1000 : 3 + (USE_LOG_INDEX ? 0xFF : 0);
  }

  @Override
  public @NotNull UpdatableIndex<FileType, Void, FileContent, ?> createIndexImplementation(@NotNull FileBasedIndexExtension<FileType, Void> extension,
                                                                                           @NotNull VfsAwareIndexStorageLayout<FileType, Void> indexStorageLayout)
    throws StorageException, IOException {
    return USE_MAPPED_INDEX ? new MappedFileTypeIndex(extension) :
           USE_LOG_INDEX ? new LogFileTypeIndex(extension) :
           new FileTypeMapReduceIndex(extension, indexStorageLayout);
  }
}
