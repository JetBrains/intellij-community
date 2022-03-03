// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.SLRUMap;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.AbstractUpdateData;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import com.intellij.util.indexing.impl.storage.AbstractIntLog;
import com.intellij.util.indexing.impl.storage.IntLog;
import com.intellij.util.io.SimpleStringPersistentEnumerator;
import com.intellij.util.io.StorageLockContext;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntConsumer;

/**
 * Implementation of {@link FileTypeIndexImpl} based on plain change log.
 * Does not support indexing unsaved changes (content-less indexes don't require them).
 */
public final class LogFileTypeIndex implements UpdatableIndex<FileType, Void, FileContent>, FileTypeNameEnumerator {
  private static final Logger LOG = Logger.getInstance(LogFileTypeIndex.class);

  private final @NotNull SLRUMap<Integer, Integer> myForwardIndexCache;
  private final @NotNull SLRUMap<Integer, IntSeq> myInvertedIndexCache;

  private final @NotNull MemorySnapshotHandler myMemorySnapshotHandler;
  private final @NotNull Disposable myDisposable;

  private final @NotNull LogBasedIntIntIndex myPersistentLog;
  private final @NotNull SimpleStringPersistentEnumerator myFileTypeEnumerator;
  private final @NotNull ConcurrentIntObjectMap<Ref<FileType>> myId2FileTypeCache = ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private final @NotNull ConcurrentIntObjectMap<Ref<String>> myId2FileNameCache = ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private final @NotNull FileBasedIndexExtension<FileType, Void> myExtension;
  private final @NotNull ReadWriteLock myLock = new ReentrantReadWriteLock();

  private final @NotNull AtomicBoolean myInMemoryMode = new AtomicBoolean();
  private final @NotNull ID<FileType, Void> myIndexId;

  public LogFileTypeIndex(@NotNull FileBasedIndexExtension<FileType, Void> extension) throws IOException {
    myExtension = extension;
    myIndexId = extension.getName();
    Path storageFile = IndexInfrastructure.getStorageFile(myIndexId);
    myPersistentLog = new LogBasedIntIntIndex(new IntLog(storageFile.resolveSibling(storageFile.getFileName().toString() + ".log.index"),
                                                         true,
                                                         new StorageLockContext(true, false, true)));
    myFileTypeEnumerator = new SimpleStringPersistentEnumerator(IndexInfrastructure.getStorageFile(myIndexId).resolveSibling("fileType.enum"));
    int cacheSize = extension.getCacheSize();
    myForwardIndexCache = new SLRUMap<>(cacheSize, (int)(Math.ceil(cacheSize * 0.25)));
    myInvertedIndexCache = new SLRUMap<>(cacheSize, (int)(Math.ceil(cacheSize * 0.25)));

    if (myExtension.dependsOnFileContent()) {
      throw new IllegalArgumentException(myExtension.getName() + " should not depend on content");
    }

    myDisposable = Disposer.newDisposable();
    myMemorySnapshotHandler = new MemorySnapshotHandler();
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
  public void updateWithMap(@NotNull AbstractUpdateData<FileType, Void> updateData) throws StorageException {
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
      IntSeq cachedFileIds = getInputIdsFromCache(fileTypeId);
      IntSeq fileIds;
      if (cachedFileIds == null) {
        MemorySnapshot snapshot = myMemorySnapshotHandler.getSnapshot();
        fileIds = snapshot != null ? snapshot.getFileIds(fileTypeId) : myPersistentLog.getFileIds(fileTypeId);
        cacheInputIds(fileTypeId, fileIds);
      }
      else {
        fileIds = cachedFileIds;
      }
      fileIds.forEach(id -> result.addValue(id, null));
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
      int data = getFileTypeIdFromCache(fileId);
      if (data != 0) return data;
      MemorySnapshot snapshot = myMemorySnapshotHandler.getSnapshot();
      data = snapshot != null ? snapshot.getIndexedData(fileId) : myPersistentLog.getIndexedData(fileId);
      updateForwardIndexCache(fileId, data);
      return data;
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
        boolean memoryDataUpdated = true;
        MemorySnapshot snapshot = myMemorySnapshotHandler.getSnapshot();
        if (snapshot != null) {
          boolean snapshotModified = snapshot.addData(fileTypeId, inputId);
          if (!snapshotModified) {
            memoryDataUpdated = false;
          }
        }
        boolean cacheModified = updateForwardIndexCache(inputId, fileTypeId);
        if (!cacheModified) {
          memoryDataUpdated = false;
        }

        if (memoryDataUpdated) {
          myPersistentLog.addData(fileTypeId, inputId);
          clearInvertedIndexCache();
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

  private void clearInvertedIndexCache() {
    synchronized (myInvertedIndexCache) {
      myInvertedIndexCache.clear();
    }
  }

  private void cacheInputIds(int fileTypeId, IntSeq inputIds) {
    IntSeq copy = inputIds.copy();
    synchronized (myInvertedIndexCache) {
      myInvertedIndexCache.put(fileTypeId, copy);
    }
  }

  private @Nullable IntSeq getInputIdsFromCache(int fileTypeId) {
    synchronized (myInvertedIndexCache) {
      return myInvertedIndexCache.get(fileTypeId);
    }
  }

  private int getFileTypeIdFromCache(int fileId) {
    synchronized (myForwardIndexCache) {
      Integer data = myForwardIndexCache.get(fileId);
      if (data != null) {
        return data;
      }
    }
    return 0;
  }

  private boolean updateForwardIndexCache(int inputId, int fileTypeId) {
    synchronized (myForwardIndexCache) {
      Integer currentFileTypeId = myForwardIndexCache.get(inputId);
      if (currentFileTypeId != null && fileTypeId == currentFileTypeId.intValue()) {
        return false;
      }
      myForwardIndexCache.put(inputId, fileTypeId);
      return true;
    }
  }

  @Override
  public void flush() throws StorageException {
    clearCache();
    try {
      myPersistentLog.flush();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void clear() throws StorageException {
    clearCache();
    myMemorySnapshotHandler.mySnapshot = null;
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
  public String getFileTypeName(int id) throws IOException {
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

    @NotNull LogFileTypeIndex.IntSeq getFileIds(int data) throws StorageException;

    int getIndexedData(int inputId) throws StorageException;
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

    @Override
    public @NotNull IntSeq getFileIds(int dataKey) throws StorageException {
      IntSet inputIds = new IntOpenHashSet();

      AbstractIntLog.IntLogEntryProcessor processor = (data, inputId) -> {
        if (data == dataKey) {
          inputIds.add(inputId);
        }
        else {
          inputIds.remove(inputId);
        }
        return true;
      };

      myLog.processEntries(processor);
      return new IntSeq.FromIntSet(inputIds);
    }

    @Override
    public int getIndexedData(int fileId) throws StorageException {
      int[] foundData = {0};
      AbstractIntLog.IntLogEntryProcessor processor = (data, inputId) -> {
        if (fileId == inputId) {
          foundData[0] = data;
        }
        return true;
      };
      myLog.processEntries(processor);
      return foundData[0];
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

    @Override
    public synchronized @NotNull IntSeq getFileIds(int data) {
      BitSet fileIds = myInvertedIndex.get(data);
      return fileIds == null ? IntSeq.EMPTY : new IntSeq.FromBitSet(fileIds);
    }

    @Override
    public synchronized int getIndexedData(int inputId) {
      return getDataFromForwardIndex(myForwardIndex, inputId);
    }

    private static int getDataFromForwardIndex(@NotNull IntList forwardIndex, int inputId) {
      if (forwardIndex.size() <= inputId) {
        return 0;
      }
      return forwardIndex.getInt(inputId);
    }
  }

  private class MemorySnapshotHandler {
    private final @NotNull List<Project> myIndexBatchUpdatingProjects = new ArrayList<>();
    private volatile @Nullable MemorySnapshot mySnapshot;

    MemorySnapshotHandler() {
      UnindexedFilesUpdaterListener unindexedFilesUpdaterListener = new UnindexedFilesUpdaterListener() {
        @Override
        public void updateStarted(@NotNull Project project) {
          loadMemorySnapshot(project);
        }

        @Override
        public void updateFinished(@NotNull Project project) {
          dropMemorySnapshot(project);
        }
      };
      ApplicationManager.getApplication().getMessageBus().connect(myDisposable).subscribe(UnindexedFilesUpdaterListener.TOPIC,
                                                                                          unindexedFilesUpdaterListener);
    }

    synchronized void loadMemorySnapshot(@NotNull Project project) {
      myIndexBatchUpdatingProjects.add(project);
      if (mySnapshot == null) {
        try {
          LOG.trace("Loading file type index snapshot");
          mySnapshot = loadIndexToMemory(myPersistentLog);
        }
        catch (StorageException e) {
          LOG.error(e);
          FileBasedIndex.getInstance().requestRebuild(FileTypeIndex.NAME);
          mySnapshot = new MemorySnapshot(new Int2ObjectOpenHashMap<>(), new IntArrayList());
        }
      }
    }

    synchronized void dropMemorySnapshot(@NotNull Project project) {
      myIndexBatchUpdatingProjects.remove(project);
      if (myIndexBatchUpdatingProjects.isEmpty() && !ApplicationManager.getApplication().isUnitTestMode()) {
        mySnapshot = null;
        LOG.trace("File type index snapshot dropped");
      }
    }

    @Nullable MemorySnapshot getSnapshot() {
      return mySnapshot;
    }

    @NotNull MemorySnapshot loadIndexToMemory(@NotNull LogBasedIntIntIndex intLogIndex) throws StorageException {
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
  }

  private void clearCache() {
    synchronized (myForwardIndexCache) {
      myForwardIndexCache.clear();
    }
    clearInvertedIndexCache();
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

    class FromIntSet implements IntSeq {
      private final IntSet myIntSet;

      private FromIntSet(@NotNull IntSet set) {myIntSet = set;}


      @Override
      public void forEach(@NotNull IntConsumer consumer) {
        myIntSet.forEach(consumer);
      }

      @Override
      public @NotNull LogFileTypeIndex.IntSeq copy() {
        return new FromIntSet(new IntOpenHashSet(myIntSet));
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
