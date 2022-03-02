// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.AbstractUpdateData;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import com.intellij.util.indexing.impl.storage.AbstractIntLog;
import com.intellij.util.indexing.impl.storage.IntLog;
import com.intellij.util.io.SimpleStringPersistentEnumerator;
import com.intellij.util.io.StorageLockContext;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntConsumer;

/**
 * Implementation of {@link FileTypeIndexImpl} based on plain change log.
 * Does not support indexing unsaved changes (content-less indexes don't require them).
 */
public final class LogFileTypeIndex implements UpdatableIndex<FileType, Void, FileContent, UpdatableIndex.EmptyData>, FileTypeNameEnumerator {
  private static final Logger LOG = Logger.getInstance(LogFileTypeIndex.class);

  private final @NotNull Disposable myDisposable;

  private final @NotNull LogBasedIntIntIndex myPersistentLog;
  private final @NotNull SimpleStringPersistentEnumerator myFileTypeEnumerator;
  private final @NotNull ConcurrentIntObjectMap<Ref<FileType>> myId2FileTypeCache = ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private final @NotNull ConcurrentIntObjectMap<Ref<String>> myId2FileNameCache = ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private final @NotNull FileBasedIndexExtension<FileType, Void> myExtension;
  private final @NotNull ReadWriteLock myLock = new ReentrantReadWriteLock();

  private final @NotNull AtomicBoolean myInMemoryMode = new AtomicBoolean();
  private final @NotNull ID<FileType, Void> myIndexId;

  private final @NotNull MemorySnapshot mySnapshot;

  public LogFileTypeIndex(@NotNull FileBasedIndexExtension<FileType, Void> extension) throws IOException, StorageException {
    myExtension = extension;
    myIndexId = extension.getName();
    Path storageFile = IndexInfrastructure.getStorageFile(myIndexId);
    myPersistentLog = new LogBasedIntIntIndex(new IntLog(storageFile.resolveSibling(storageFile.getFileName().toString() + ".log.index"),
                                                         true,
                                                         new StorageLockContext(false, true)));
    myFileTypeEnumerator = new SimpleStringPersistentEnumerator(IndexInfrastructure.getStorageFile(myIndexId).resolveSibling("fileType.enum"));

    if (myExtension.dependsOnFileContent()) {
      throw new IllegalArgumentException(myExtension.getName() + " should not depend on content");
    }

    myDisposable = Disposer.newDisposable();

    mySnapshot = loadIndexToMemory(myPersistentLog);
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
  public @NotNull UpdatableIndex.EmptyData instantiateFileData() {
    return UpdatableIndex.EmptyData.INSTANCE;
  }

  @Override
  public void writeData(@NotNull UpdatableIndex.EmptyData unused, @NotNull IndexedFile file) {
  }

  @Override
  public void setIndexedStateForFileOnCachedData(int fileId, @NotNull UpdatableIndex.EmptyData data) {
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
      if (indexedFileTypeId == 0) return isIndexed;
      int actualFileTypeId = getFileTypeId(file.getFileType());

      return indexedFileTypeId == actualFileTypeId
             ? FileIndexingState.UP_TO_DATE
             : FileIndexingState.OUT_DATED;
    } catch (StorageException e) {
      LOG.error(e);
      return FileIndexingState.OUT_DATED;
    }
  }

  @Override
  public long getModificationStamp() {
    return myPersistentLog.getModificationStamp();
  }

  @Override
  public void removeTransientDataForFile(int inputId) { }

  @Override
  public @NotNull IndexExtension<FileType, Void, FileContent> getExtension() {
    return myExtension;
  }

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
  public @NotNull ValueContainer<Void> getData(@NotNull FileType type) throws StorageException {
    int fileTypeId = getFileTypeId(type);
    ValueContainerImpl<Void> result = new ValueContainerImpl<>();

    myLock.readLock().lock();
    try {
      mySnapshot.getFileIds(fileTypeId).forEach(id -> result.addValue(id, null));
    }
    finally {
      myLock.readLock().unlock();
    }

    return result;
  }

  @Override
  public @NotNull Computable<Boolean> mapInputAndPrepareUpdate(int inputId, @Nullable FileContent content) {
    try {
      int fileTypeId = getFileTypeId(content == null ? null : content.getFileType());
      return () -> updateIndex(fileTypeId, inputId);
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public @NotNull Computable<Boolean> prepareUpdate(int inputId, @NotNull InputData<FileType, Void> data) {
    throw new UnsupportedOperationException();
  }

  private FileType getFileTypeById(int id) {
    assert id < Short.MAX_VALUE : "file type id = " + id;
    Ref<FileType> fileType = myId2FileTypeCache.get(id);
    if (fileType == null) {
      String fileTypeName = myFileTypeEnumerator.valueOf(id);
      FileType fileTypeByName = fileTypeName == null ? null : FileTypeManager.getInstance().findFileTypeByName(fileTypeName);
      myId2FileTypeCache.put(id, fileType = Ref.create(fileTypeByName));
    }
    return fileType.get();
  }

  private int getIndexedFileTypeId(int fileId) throws StorageException {
    myLock.readLock().lock();
    try {
      return mySnapshot.getIndexedData(fileId);
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  private int getFileTypeId(@Nullable FileType fileType) throws StorageException {
    if (fileType == null) return 0;
    try {
      return getFileTypeId(fileType.getName());
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @NotNull
  private Boolean updateIndex(int fileTypeId, int inputId) {
    myLock.writeLock().lock();
    try {
      if (myInMemoryMode.get()) {
        throw new IllegalStateException("file type index should not be updated for unsaved changes");
      }
      else {
        boolean snapshotModified = mySnapshot.addData(fileTypeId, inputId);
        if (snapshotModified) {
          myPersistentLog.addData(fileTypeId, inputId);
        }
      }
    }
    catch (StorageException e) {
      LOG.error(e);
      return Boolean.FALSE;
    }
    finally {
      myLock.writeLock().unlock();
    }
    return Boolean.TRUE;
  }

  @Override
  public void flush() throws StorageException {
    try {
      myPersistentLog.flush();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void clear() throws StorageException {
    mySnapshot.clear();
    myPersistentLog.clear();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDisposable);
    try {
      myPersistentLog.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int getFileTypeId(String name) throws IOException {
    return myFileTypeEnumerator.enumerate(name);
  }

  @Override
  public String getFileTypeName(int id) {
    assert id < Short.MAX_VALUE;
    Ref<String> fileType = myId2FileNameCache.get(id);
    if (fileType == null) {
      String fileTypeName = myFileTypeEnumerator.valueOf(id);
      myId2FileNameCache.put(id, fileType = Ref.create(fileTypeName));
    }
    return fileType.get();
  }

  private interface IntIntIndex {
    boolean addData(int data, int inputId) throws StorageException;
  }

  private static class LogBasedIntIntIndex implements IntIntIndex {
    private final AbstractIntLog myLog;

    private LogBasedIntIntIndex(@NotNull AbstractIntLog log) {
      myLog = log;
    }

    @Override
    public boolean addData(int data, int inputId) throws StorageException {
      myLog.addData(data, inputId);
      return true;
    }

    private void processEntries(@NotNull AbstractIntLog.IntLogEntryProcessor processor) throws StorageException {
      myLog.processEntries(processor);
    }

    public long getModificationStamp() {
      return myLog.getModificationStamp();
    }

    public void clear() {
      myLog.clear();
    }

    public void close() throws IOException {
      myLog.close();
    }

    public void flush() throws IOException {
      myLog.flush();
    }
  }

  private static class MemorySnapshot implements IntIntIndex {
    private final @NotNull Int2ObjectMap<BitSet> myInvertedIndex;
    private final @NotNull IntList myForwardIndex;

    private MemorySnapshot(@NotNull Int2ObjectMap<BitSet> invertedIndex, @NotNull IntList forwardIndex) {
      myInvertedIndex = invertedIndex;
      myForwardIndex = forwardIndex;
    }

    @Override
    public synchronized boolean addData(int data, int inputId) {
      int indexedData = getIndexedData(inputId);
      if (indexedData != 0) {
        BitSet indexedSet = myInvertedIndex.get(indexedData);
        assert indexedSet != null;
        indexedSet.clear(inputId);
      }
      boolean updated = setForwardIndexData(myForwardIndex, data, inputId);
      if (data != 0) {
        myInvertedIndex.computeIfAbsent(data, __ -> new BitSet()).set(inputId);
      }
      return updated;
    }

    public synchronized @NotNull IntSeq getFileIds(int data) {
      BitSet fileIds = myInvertedIndex.get(data);
      return fileIds == null ? IntSeq.EMPTY : new IntSeq.FromBitSet(fileIds);
    }

    public synchronized int getIndexedData(int inputId) {
      return getDataFromForwardIndex(myForwardIndex, inputId);
    }

    private static int getDataFromForwardIndex(@NotNull IntList forwardIndex, int inputId) {
      if (forwardIndex.size() <= inputId) {
        return 0;
      }
      return forwardIndex.getInt(inputId);
    }

    private void clear() {
      myInvertedIndex.clear();
      myForwardIndex.clear();
    }
  }

  private static @NotNull MemorySnapshot loadIndexToMemory(@NotNull LogBasedIntIntIndex intLogIndex) throws StorageException {
    Int2ObjectMap<BitSet> invertedIndex = new Int2ObjectOpenHashMap<>();
    IntList forwardIndex = new IntArrayList();
    intLogIndex.processEntries((data, inputId) -> {
      if (data != 0) {
        setForwardIndexData(forwardIndex, data, inputId);
        invertedIndex.computeIfAbsent(data, __ -> new BitSet()).set(inputId);
      }
      else {
        int previousData = MemorySnapshot.getDataFromForwardIndex(forwardIndex, inputId);
        if (previousData != 0) {
          forwardIndex.set(inputId, 0);
          invertedIndex.get(previousData).clear(inputId);
        }
      }
      return true;
    });
    return new MemorySnapshot(invertedIndex, forwardIndex);
  }

  private static boolean setForwardIndexData(@NotNull IntList forwardIndex, int data, int inputId) {
    if (inputId >= forwardIndex.size()) {
      forwardIndex.size((inputId + 1) * 3 / 2);
    }
    return data != forwardIndex.set(inputId, data);
  }

  private interface IntSeq {
    void forEach(@NotNull IntConsumer consumer);

    @NotNull LogFileTypeIndex.IntSeq copy();

    class FromBitSet implements IntSeq {
      private final BitSet myBitSet;

      private FromBitSet(@NotNull BitSet set) {myBitSet = set;}

      @Override
      public void forEach(@NotNull IntConsumer consumer) {
        myBitSet.stream().forEach(consumer);
      }

      @Override
      public @NotNull LogFileTypeIndex.IntSeq copy() {
        return new FromBitSet((BitSet)myBitSet.clone());
      }
    }

    IntSeq EMPTY = new IntSeq() {
      @Override
      public void forEach(@NotNull IntConsumer consumer) { }

      @Override
      public @NotNull LogFileTypeIndex.IntSeq copy() {
        return this;
      }
    };
  }
}
