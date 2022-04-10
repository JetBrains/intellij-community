// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.MapInputDataDiffBuilder;
import com.intellij.util.indexing.impl.storage.TransientFileContentIndex;
import com.intellij.util.indexing.impl.storage.VfsAwareMapReduceIndex;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.StorageLockContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

class FileTypeMapReduceIndex extends TransientFileContentIndex<FileType, Void, VfsAwareMapReduceIndex.IndexerIdHolder>
  implements FileTypeNameEnumerator {
  private static final Logger LOG = Logger.getInstance(FileTypeIndexImpl.class);
  private PersistentStringEnumerator myFileTypeNameEnumerator;

  FileTypeMapReduceIndex(@NotNull FileBasedIndexExtension<FileType, Void> extension,
                         @NotNull VfsAwareIndexStorageLayout<FileType, Void> layout) throws IOException {
    super(extension, layout, null);
    myFileTypeNameEnumerator = createFileTypeNameEnumerator();
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
    myFileTypeNameEnumerator.force();
  }

  @Override
  protected void doDispose() throws StorageException {
    try {
      super.doDispose();
    } finally {
      IOUtil.closeSafe(LOG, myFileTypeNameEnumerator);
    }
  }

  @Override
  protected void doClear() throws StorageException, IOException {
    super.doClear();
    IOUtil.closeSafe(LOG, myFileTypeNameEnumerator);
    IOUtil.deleteAllFilesStartingWith(getFileTypeNameEnumeratorPath().toFile());
    myFileTypeNameEnumerator = createFileTypeNameEnumerator();
  }

  @Override
  public int getFileTypeId(String name) throws IOException {
    return myFileTypeNameEnumerator.enumerate(name);
  }

  @Override
  public String getFileTypeName(int id) throws IOException {
    return myFileTypeNameEnumerator.valueOf(id);
  }

  @Override
  public @NotNull VfsAwareMapReduceIndex.IndexerIdHolder instantiateFileData() {
    return new IndexerIdHolder();
  }

  @NotNull
  private static PersistentStringEnumerator createFileTypeNameEnumerator() throws IOException {
    return new PersistentStringEnumerator(getFileTypeNameEnumeratorPath(),  128, true, new StorageLockContext());
  }

  private static @NotNull Path getFileTypeNameEnumeratorPath() throws IOException {
    return IndexInfrastructure.getIndexRootDir(FileTypeIndex.NAME).resolve("file.type.names");
  }
}
