// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.AbstractUpdateData;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import com.intellij.util.io.MeasurableIndexStore;
import com.intellij.util.io.SimpleStringPersistentEnumerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntConsumer;

public abstract class FileTypeIndexImplBase implements UpdatableIndex<FileType, Void, FileContent, Void>, FileTypeNameEnumerator,
                                                       MeasurableIndexStore {
  private static final Logger LOG = Logger.getInstance(FileTypeIndexImplBase.class);

  protected final @NotNull FileBasedIndexExtension<FileType, Void> myExtension;
  protected final @NotNull ID<FileType, Void> myIndexId;
  protected final @NotNull ReadWriteLock myLock = new ReentrantReadWriteLock();
  protected final @NotNull SimpleStringPersistentEnumerator myFileTypeEnumerator;
  private final @NotNull ConcurrentIntObjectMap<Ref<FileType>> myId2FileTypeCache =
    ConcurrentCollectionFactory.createConcurrentIntObjectMap(); // Ref is here to store nulls
  protected final @NotNull AtomicBoolean myInMemoryMode = new AtomicBoolean();
  protected final @NotNull FileTypeIndex.IndexChangeListener myIndexChangedPublisher;

  public FileTypeIndexImplBase(@NotNull FileBasedIndexExtension<FileType, Void> extension) throws IOException {
    myExtension = extension;
    if (myExtension.dependsOnFileContent()) {
      throw new IllegalArgumentException(myExtension.getName() + " should not depend on content");
    }
    myIndexId = extension.getName();
    myFileTypeEnumerator = new SimpleStringPersistentEnumerator(getStorageFile().resolveSibling("fileType.enum"));
    myIndexChangedPublisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(FileTypeIndex.INDEX_CHANGE_TOPIC);
  }

  protected abstract int getIndexedFileTypeId(int fileId) throws StorageException;

  protected abstract void processFileIdsForFileTypeId(int fileTypeId, @NotNull IntConsumer consumer);

  protected @NotNull Path getStorageFile() throws IOException {
    return IndexInfrastructure.getStorageFile(myIndexId);
  }

  protected @Nullable FileType getFileTypeById(int id) {
    Ref<FileType> fileType = myId2FileTypeCache.get(id);
    if (fileType == null) {
      String fileTypeName = myFileTypeEnumerator.valueOf(id);
      FileType fileTypeByName = fileTypeName == null ? null : FileTypeManager.getInstance().findFileTypeByName(fileTypeName);
      myId2FileTypeCache.put(id, fileType = Ref.create(fileTypeByName));
    }
    return fileType.get();
  }

  @Override
  public String getFileTypeName(int id) {
    FileType fileType = getFileTypeById(id);
    return fileType == null ? null : fileType.getName();
  }

  @Override
  public int getFileTypeId(String name) throws IOException {
    return myFileTypeEnumerator.enumerate(name);
  }

  protected int getFileTypeId(@Nullable FileType fileType) throws StorageException {
    if (fileType == null) return 0;
    try {
      return getFileTypeId(fileType.getName());
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public boolean processAllKeys(@NotNull Processor<? super FileType> processor,
                                @NotNull GlobalSearchScope scope,
                                @Nullable IdFilter idFilter) throws StorageException {
    for (String fileTypeName : myFileTypeEnumerator.entries()) {
      FileType fileType = FileTypeManager.getInstance().findFileTypeByName(fileTypeName);
      if (fileType != null && !processor.process(fileType)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public @NotNull ReadWriteLock getLock() {
    return myLock;
  }

  @Override
  public @NotNull Map<FileType, Void> getIndexedFileData(int fileId) throws StorageException {
    int foundData = getIndexedFileTypeId(fileId);
    if (foundData == 0) {
      return Collections.emptyMap();
    }
    return Collections.singletonMap(getFileTypeById(foundData), null);
  }

  @Override
  public Void getFileIndexMetaData(@NotNull IndexedFile file) {
    return null;
  }

  @Override
  public void setIndexedStateForFileOnFileIndexMetaData(int fileId, @Nullable Void data) {
    IndexingStamp.setFileIndexedStateCurrent(fileId, myIndexId);
  }

  @Override
  public void setIndexedStateForFile(int fileId, @NotNull IndexedFile file) {
    IndexingStamp.setFileIndexedStateCurrent(fileId, myIndexId);
  }

  @Override
  public void invalidateIndexedStateForFile(int fileId) {
    IndexingStamp.setFileIndexedStateOutdated(fileId, myIndexId);
  }

  @Override
  public void setUnindexedStateForFile(int fileId) {
    IndexingStamp.setFileIndexedStateUnindexed(fileId, myIndexId);
  }

  @Override
  public @NotNull FileIndexingState getIndexingStateForFile(int fileId,
                                                            @NotNull IndexedFile file) {
    @NotNull FileIndexingState isIndexed = IndexingStamp.isFileIndexedStateCurrent(fileId, myIndexId);
    if (isIndexed != FileIndexingState.UP_TO_DATE) return isIndexed;
    try {
      int indexedFileTypeId = getIndexedFileTypeId(fileId);
      if (indexedFileTypeId == 0) return FileIndexingState.NOT_INDEXED;
      int actualFileTypeId = getFileTypeId(file.getFileType());

      return indexedFileTypeId == actualFileTypeId
             ? FileIndexingState.UP_TO_DATE
             : FileIndexingState.OUT_DATED;
    }
    catch (StorageException e) {
      LOG.error(e);
      return FileIndexingState.OUT_DATED;
    }
  }

  @Override
  public @NotNull IndexExtension<FileType, Void, FileContent> getExtension() {
    return myExtension;
  }

  @Override
  public int keysCountApproximately() {
    return myFileTypeEnumerator.getSize();
  }

  @Override
  public void removeTransientDataForFile(int inputId) { }

  @Override
  public void removeTransientDataForKeys(int inputId,
                                         @NotNull InputDataDiffBuilder<FileType, Void> diffBuilder) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateWithMap(@NotNull AbstractUpdateData<FileType, Void> updateData) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setBufferingEnabled(boolean enabled) {
    myInMemoryMode.set(enabled);
  }

  @Override
  public void cleanupMemoryStorage() { }

  @Override
  public void cleanupForNextTest() { }

  @Override
  public @NotNull Computable<Boolean> prepareUpdate(int inputId, @NotNull InputData<FileType, Void> data) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull ValueContainer<Void> getData(@NotNull FileType type) throws StorageException {
    int fileTypeId = getFileTypeId(type);
    ValueContainerImpl<Void> result = new ValueContainerImpl<>(false);

    myLock.readLock().lock();
    try {
      processFileIdsForFileTypeId(fileTypeId, id -> result.addValue(id, null));
    }
    finally {
      myLock.readLock().unlock();
    }

    return result;
  }

  protected void notifyInvertedIndexChangedForFileTypeId(int id) {
    if (id == 0) {
      return;
    }
    var fileType = getFileTypeById(id);
    if (fileType != null) {
      myIndexChangedPublisher.onChangedForFileType(fileType);
    }
  }
}
