// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Computable;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.storage.AbstractIntLog;
import com.intellij.util.indexing.impl.storage.IntLog;
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
import java.util.function.IntConsumer;

/**
 * Implementation of {@link FileTypeIndexImpl} based on plain change log.
 * Does not support indexing unsaved changes (content-less indexes don't require them).
 */
public final class LogFileTypeIndex extends FileTypeIndexImplBase {
  private static final Logger LOG = Logger.getInstance(LogFileTypeIndex.class);

  private final @NotNull LogBasedIntIntIndex myPersistentLog;
  private final @NotNull MemorySnapshot mySnapshot;

  public LogFileTypeIndex(@NotNull FileBasedIndexExtension<FileType, Void> extension) throws IOException, StorageException {
    super(extension);
    Path storageFile = getStorageFile();
    myPersistentLog = new LogBasedIntIntIndex(new IntLog(storageFile.resolveSibling(storageFile.getFileName().toString() + ".log.index"),
                                                         true,
                                                         new StorageLockContext(false, true)));
    mySnapshot = loadIndexToMemory(myPersistentLog, id -> {
      notifyInvertedIndexChangedForFileTypeId(id);
    });
  }

  @Override
  public long getModificationStamp() {
    return myPersistentLog.getModificationStamp();
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
  protected int getIndexedFileTypeId(int fileId) {
    myLock.readLock().lock();
    try {
      return mySnapshot.getIndexedData(fileId);
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  @Override
  protected void processFileIdsForFileTypeId(int fileTypeId, @NotNull IntConsumer consumer) {
    mySnapshot.getFileIds(fileTypeId).forEach(consumer);
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
    try {
      myPersistentLog.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
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
    private final @NotNull IntConsumer myInvertedIndexChangeCallback;

    private MemorySnapshot(@NotNull Int2ObjectMap<BitSet> invertedIndex,
                           @NotNull IntList forwardIndex,
                           @NotNull IntConsumer invertedIndexChangeCallback) {
      myInvertedIndex = invertedIndex;
      myForwardIndex = forwardIndex;
      myInvertedIndexChangeCallback = invertedIndexChangeCallback;
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
      triggerOnInvertedIndexChangeCallback(data, indexedData);
      return updated;
    }

    private void triggerOnInvertedIndexChangeCallback(int newData, int oldData) {
      if (oldData != newData) {
        if (oldData != 0) {
          myInvertedIndexChangeCallback.accept(oldData);
        }
        if (newData != 0) {
          myInvertedIndexChangeCallback.accept(newData);
        }
      }
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

  private static @NotNull MemorySnapshot loadIndexToMemory(@NotNull LogBasedIntIntIndex intLogIndex,
                                                           @NotNull IntConsumer invertedIndexChangeCallback) throws StorageException {
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
    return new MemorySnapshot(invertedIndex, forwardIndex, invertedIndexChangeCallback);
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

      private FromBitSet(@NotNull BitSet set) { myBitSet = set; }

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
