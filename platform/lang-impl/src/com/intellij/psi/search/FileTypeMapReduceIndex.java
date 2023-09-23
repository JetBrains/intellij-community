// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.MapInputDataDiffBuilder;
import com.intellij.util.indexing.impl.storage.TransientFileContentIndex;
import com.intellij.util.indexing.impl.storage.VfsAwareMapReduceIndex;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

final class FileTypeMapReduceIndex extends TransientFileContentIndex<FileType, Void, VfsAwareMapReduceIndex.IndexerIdHolder>
  implements FileTypeNameEnumerator {
  private static final Logger LOG = Logger.getInstance(FileTypeIndexImpl.class);

  private DurableDataEnumerator<String> fileTypeNameEnumerator;

  FileTypeMapReduceIndex(@NotNull FileBasedIndexExtension<FileType, Void> extension,
                         @NotNull VfsAwareIndexStorageLayout<FileType, Void> layout) throws IOException {
    super(extension, layout);
    fileTypeNameEnumerator = createFileTypeNameEnumerator();
  }

  @Override
  public @NotNull FileIndexingState getIndexingStateForFile(int fileId, @NotNull IndexedFile file) {
    @NotNull FileIndexingState isIndexed = super.getIndexingStateForFile(fileId, file);
    if (isIndexed != FileIndexingState.UP_TO_DATE) return isIndexed;
    try {
      Collection<FileType> inputData = ((MapInputDataDiffBuilder<FileType, Void>) getKeysDiffBuilder(fileId)).getKeys();
      FileType indexedFileType = ContainerUtil.getFirstItem(inputData);
      return getExtension().getKeyDescriptor().isEqual(indexedFileType, file.getFileType())
             ? FileIndexingState.UP_TO_DATE
             : FileIndexingState.OUT_DATED;
    } catch (IOException e) {
      LOG.error(e);
      return FileIndexingState.OUT_DATED;
    }
  }

  @Override
  protected void doFlush() throws IOException, StorageException {
    super.doFlush();
    fileTypeNameEnumerator.force();
  }

  @Override
  protected void doDispose() throws StorageException {
    try {
      super.doDispose();
    } finally {
      IOUtil.closeSafe(LOG, fileTypeNameEnumerator);
    }
  }

  @Override
  protected void doClear() throws StorageException, IOException {
    super.doClear();
    IOUtil.closeSafe(LOG, fileTypeNameEnumerator);
    IOUtil.deleteAllFilesStartingWith(getFileTypeNameEnumeratorPath());
    fileTypeNameEnumerator = createFileTypeNameEnumerator();
  }

  @Override
  public int getFileTypeId(String name) throws IOException {
    return fileTypeNameEnumerator.enumerate(name);
  }

  @Override
  public @Nullable String getFileTypeName(int id) throws IOException {
    return fileTypeNameEnumerator.valueOf(id);
  }

  private static @NotNull DurableDataEnumerator<String> createFileTypeNameEnumerator() throws IOException {
    return new PersistentStringEnumerator(getFileTypeNameEnumeratorPath(),  128, true, new StorageLockContext());
  }

  private static @NotNull Path getFileTypeNameEnumeratorPath() throws IOException {
    return IndexInfrastructure.getIndexRootDir(FileTypeIndex.NAME).resolve("file.type.names");
  }
}
