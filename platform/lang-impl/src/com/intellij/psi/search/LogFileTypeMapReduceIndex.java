// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.util.IntPair;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntIntHashMap;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.AbstractUpdateData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import com.intellij.util.indexing.impl.storage.AbstractIntLog;
import com.intellij.util.indexing.impl.storage.IntLog;
import com.intellij.util.indexing.impl.storage.MemoryIntLog;
import com.intellij.util.indexing.snapshot.SnapshotInputMappingException;
import com.intellij.util.io.SimpleStringPersistentEnumerator;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LogFileTypeMapReduceIndex implements UpdatableIndex<FileType, Void, FileContent>, FileTypeNameEnumerator {
  private static final Logger LOG = Logger.getInstance(LogFileTypeMapReduceIndex.class);

  private final @NotNull MemorySnapshotHandler myMemorySnapshotHandler = new MemorySnapshotHandler();
  private final @NotNull AbstractIntLog myLog;
  private final @NotNull SimpleStringPersistentEnumerator myFileTypeEnumerator;
  private final @NotNull ConcurrentIntObjectMap<Ref<FileType>> myId2FileTypeCache = ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private final @NotNull ConcurrentIntObjectMap<Ref<String>> myId2FileNameCache = ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private final @NotNull FileBasedIndexExtension<FileType, Void> myExtension;
  private final @NotNull ReadWriteLock myLock = new ReentrantReadWriteLock();

  private final @NotNull AtomicBoolean myInMemoryMode = new AtomicBoolean();
  private final @NotNull AbstractIntLog myMemoryLog = new MemoryIntLog();
  private final @NotNull ID<FileType, Void> myIndexId;

  public LogFileTypeMapReduceIndex(@NotNull FileBasedIndexExtension<FileType, Void> extension) throws IOException {
    myExtension = extension;
    myIndexId = extension.getName();
    myLog = new IntLog(IndexInfrastructure.getStorageFile(myIndexId), true);
    myFileTypeEnumerator = new SimpleStringPersistentEnumerator(IndexInfrastructure.getStorageFile(myIndexId).resolveSibling("fileType.enum"));
  }

  @Override
  public boolean processAllKeys(@NotNull Processor<? super FileType> processor,
                                @NotNull GlobalSearchScope scope,
                                @Nullable IdFilter idFilter) throws StorageException {
    for (String fileTypeName : myFileTypeEnumerator.entries()) {
      FileType fileType = FileTypeManager.getInstance().findFileTypeByName(fileTypeName);
      if (fileType != null) {
        if (!processor.process(fileType)) {
          return false;
        }
      }
    }
    return false;
  }

  @Override
  public @NotNull ReadWriteLock getLock() {
    return myLock;
  }

  private FileType getFileTypeById(int id) {
    assert id < Short.MAX_VALUE : "file type id = " + id;
    Ref<FileType> fileType = myId2FileTypeCache.get(id);
    if (fileType == null) {
      String fileTypeName = myFileTypeEnumerator.valueOf((short)id);
      FileType fileTypeByName = fileTypeName == null ? null : FileTypeManager.getInstance().findFileTypeByName(fileTypeName);
      myId2FileTypeCache.put(id, fileType = Ref.create(fileTypeByName));
    }
    return fileType.get();
  }

  @Override
  public @NotNull Map<FileType, Void> getIndexedFileData(int fileId) throws StorageException {
    int[] foundData = {0};
    AbstractIntLog.IntLogEntryProcessor processor = (data, inputId) -> {
      if (fileId == inputId) {
        foundData[0] = data;
      }
      return true;
    };
    MemorySnapshot snapshot = myMemorySnapshotHandler.getSnapshot();
    if (snapshot != null) {
      int fileTypeId = snapshot.getIndexedData(fileId);
      if (fileTypeId != 0) {
        foundData[0] = fileTypeId;
      }
    }
    else {
      myLog.processEntries(processor);
    }
    myMemoryLog.processEntries(processor);
    if (foundData[0] == 0) {
      return Collections.emptyMap();
    }
    return Collections.singletonMap(getFileTypeById(foundData[0]), null);
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
      Collection<FileType> inputData = getIndexedFileData(fileId).keySet();
      FileType indexedFileType = ContainerUtil.getFirstItem(inputData);
      return getExtension().getKeyDescriptor().isEqual(indexedFileType, file.getFileType())
             ? FileIndexingState.UP_TO_DATE
             : FileIndexingState.OUT_DATED;
    } catch (StorageException e) {
      LOG.error(e);
      return FileIndexingState.OUT_DATED;
    }
  }

  @Override
  public long getModificationStamp() {
    return myLog.getModificationStamp() + myMemoryLog.getModificationStamp();
  }

  @Override
  public void removeTransientDataForFile(int inputId) {
    List<IntPair> toDelete = new ArrayList<>();
    try {
      myMemoryLog.processEntries((data, inputId1) -> {
        toDelete.add(new IntPair(data, inputId1));
        return true;
      });
      for (IntPair pair : toDelete) {
        myMemoryLog.removeData(pair.first, pair.second);
      }
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
  }

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
    if (!enabled) {
      myMemoryLog.clear();
    }
  }

  @Override
  public void cleanupMemoryStorage() {
    myMemoryLog.clear();
  }

  @Override
  public void cleanupForNextTest() {
    myMemoryLog.clear();
  }

  @Override
  public @NotNull ValueContainer<Void> getData(@NotNull FileType type) throws StorageException {
    int fileTypeId;
    try {
      fileTypeId = getFileTypeId(type.getName());
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    IntSet inputIds = new IntOpenHashSet();
    AbstractIntLog.IntLogEntryProcessor processor = (data, inputId) -> {
      if (data == fileTypeId) {
        inputIds.add(inputId);
      }
      else {
        inputIds.remove(inputId);
      }
      return true;
    };
    MemorySnapshot snapshot = myMemorySnapshotHandler.getSnapshot();
    if (snapshot != null) {
      IntIterator intIterator = snapshot.getDataIds(fileTypeId).intIterator();
      while (intIterator.hasNext()) {
        int inputId = intIterator.nextInt();
        inputIds.add(inputId);
      }
    }
    else {
      myLog.processEntries(processor);
    }
    myMemoryLog.processEntries(processor);

    ValueContainerImpl<Void> result = new ValueContainerImpl<>();
    for (Integer inputId : inputIds) {
      result.addValue(inputId, null);
    }
    return result;
  }

  @Override
  public @NotNull Computable<Boolean> mapInputAndPrepareUpdate(int inputId,
                                                               @Nullable FileContent content) {
    int fileTypeId;
    if (content == null) {
      fileTypeId = 0;
    }
    else {
      FileType fileType = content.getFileType();
      try {
        fileTypeId = getFileTypeId(fileType.getName());
      }
      catch (IOException e) {
        throw new SnapshotInputMappingException(e);
      }
    }
    return new Computable<>() {
      @Override
      public Boolean compute() {
        try {
          if (myInMemoryMode.get()) {
            myMemoryLog.addData(fileTypeId, inputId);
          }
          else {
            myLog.addData(fileTypeId, inputId);
            MemorySnapshot snapshot = myMemorySnapshotHandler.getSnapshot();
            if (snapshot != null) {
              snapshot.addData(fileTypeId, inputId);
            }
          }
        }
        catch (StorageException e) {
          LOG.error(e);
          return Boolean.FALSE;
        }
        return Boolean.TRUE;
      }
    };
  }

  @Override
  public void flush() throws StorageException {
    try {
      myLog.flush();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void clear() throws StorageException {
    myLog.clear();
    myMemoryLog.clear();
  }

  @Override
  public void dispose() {
    try {
      myLog.close();
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
      String fileTypeName = myFileTypeEnumerator.valueOf((short)id);
      myId2FileNameCache.put(id, fileType = Ref.create(fileTypeName));
    }
    return fileType.get();
  }

  private class MemorySnapshotHandler {
    private volatile @Nullable MemorySnapshot mySnapshot;
    private final @NotNull Set<Project> myIndexBatchUpdatingProjects = new HashSet<>();

    MemorySnapshotHandler() {
      UnindexedFilesUpdaterListener unindexedFilesUpdaterListener = new UnindexedFilesUpdaterListener() {
        @Override
        public void updateStarted(@NotNull Project project) {
          startMemoryLoading(project);
        }

        @Override
        public void updateFinished(@NotNull Project project) {
          finishMemoryLoading(project);
        }
      };
      ApplicationManager.getApplication().getMessageBus().connect().subscribe(UnindexedFilesUpdaterListener.TOPIC,
                                                                              unindexedFilesUpdaterListener);
    }

    synchronized void startMemoryLoading(@NotNull Project project) {
      if (myIndexBatchUpdatingProjects.add(project)) {
        try {
          mySnapshot = loadIndexToMemory(myLog);
        }
        catch (StorageException e) {
          LOG.error(e);
        }
      }
    }

    synchronized void finishMemoryLoading(@NotNull Project project) {
      if (myIndexBatchUpdatingProjects.remove(project) && myIndexBatchUpdatingProjects.isEmpty()) {
        mySnapshot = null;
      }
    }

    @Nullable MemorySnapshot getSnapshot() {
      return mySnapshot;
    }

    @NotNull MemorySnapshot loadIndexToMemory(@NotNull AbstractIntLog intLog) throws StorageException {
      Int2ObjectMap<IntSet> invertedIndex = new Int2ObjectOpenHashMap<>();
      Int2IntMap forwardIndex = new IntIntHashMap();
      intLog.processEntries((data, inputId) -> {
        forwardIndex.put(inputId, data);
        invertedIndex.computeIfAbsent(data, __ -> new IntOpenHashSet()).add(inputId);
        return true;
      });
      return new MemorySnapshot(invertedIndex, forwardIndex);
    }
  }

  private static class MemorySnapshot {
    private final @NotNull Int2ObjectMap<IntSet> myInvertedIndex;
    private final @NotNull Int2IntMap myForwardIndex;

    private MemorySnapshot(@NotNull Int2ObjectMap<IntSet> invertedIndex, @NotNull Int2IntMap forwardIndex) {
      myInvertedIndex = invertedIndex;
      myForwardIndex = forwardIndex;
    }

    synchronized void addData(int data, int inputId) {
      myInvertedIndex.computeIfAbsent(data, __ -> new IntOpenHashSet()).add(inputId);
      myForwardIndex.put(inputId, data);
    }

    synchronized @NotNull IntSet getDataIds(int data) {
      return myInvertedIndex.getOrDefault(data, IntSets.EMPTY_SET);
    }

    synchronized int getIndexedData(int inputId) {
      return myForwardIndex.get(inputId);
    }
  }
}
