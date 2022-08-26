// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.AbstractUpdateData;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import com.intellij.util.io.MeasurableIndexStore;
import com.intellij.util.io.SimpleStringPersistentEnumerator;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntConsumer;

public final class MappedFileTypeIndex implements UpdatableIndex<FileType, Void, FileContent, Void>, FileTypeNameEnumerator,
                                                  MeasurableIndexStore {
  private static final Logger LOG = Logger.getInstance(MappedFileTypeIndex.class);

  private static class FileDetails {
    public FileType myFileType;
    public String myFileTypeName;

    private FileDetails(FileType fileType, String fileTypeName) {
      myFileType = fileType;
      myFileTypeName = fileTypeName;
    }
  }

  private final @NotNull SimpleStringPersistentEnumerator myFileTypeEnumerator;
  private final @NotNull ConcurrentIntObjectMap<FileDetails> myId2FileDetailsCache =
    ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private final @NotNull FileBasedIndexExtension<FileType, Void> myExtension;
  private final @NotNull ReadWriteLock myLock = new ReentrantReadWriteLock();

  private final @NotNull AtomicBoolean myInMemoryMode = new AtomicBoolean();
  private final @NotNull ID<FileType, Void> myIndexId;
  private final @NotNull MappedFileTypeIndex.MemorySnapshot mySnapshot;
  private final @NotNull ForwardIndexFileController myForwardIndexController;

  public MappedFileTypeIndex(@NotNull FileBasedIndexExtension<FileType, Void> extension) throws IOException, StorageException {
    myExtension = extension;
    myIndexId = extension.getName();
    Path storageFile = IndexInfrastructure.getStorageFile(myIndexId);
    myForwardIndexController = new ForwardIndexFileController(
      storageFile.resolveSibling(storageFile.getFileName().toString() + ".index")
    );
    myFileTypeEnumerator =
      new SimpleStringPersistentEnumerator(storageFile.resolveSibling("fileType.enum"));

    if (myExtension.dependsOnFileContent()) {
      throw new IllegalArgumentException(myExtension.getName() + " should not depend on content");
    }

    mySnapshot = loadIndexToMemory(myForwardIndexController);
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
      if (indexedFileTypeId == 0) return isIndexed;
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
  public long getModificationStamp() {
    return myForwardIndexController.getModificationStamp();
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
    ValueContainerImpl<Void> result = new ValueContainerImpl<>(false);

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
      short fileTypeId = getFileTypeId(content == null ? null : content.getFileType());
      return () -> updateIndex(inputId, fileTypeId);
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public @NotNull Computable<Boolean> prepareUpdate(int inputId, @NotNull InputData<FileType, Void> data) {
    throw new UnsupportedOperationException();
  }

  private FileDetails getFileDetails(int id) {
    assert id < Short.MAX_VALUE : "file type id = " + id;
    FileDetails fileDetails = myId2FileDetailsCache.get(id);
    if (fileDetails == null) {
      String fileTypeName = myFileTypeEnumerator.valueOf(id);
      FileType fileTypeByName = fileTypeName == null ? null : FileTypeManager.getInstance().findFileTypeByName(fileTypeName);
      fileDetails = new FileDetails(fileTypeByName, fileTypeName);
      myId2FileDetailsCache.put(id, fileDetails);
    }
    return fileDetails;
  }

  private FileType getFileTypeById(int id) {
    return getFileDetails(id).myFileType;
  }

  @Override
  public String getFileTypeName(int id) {
    return getFileDetails(id).myFileTypeName;
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

  private short getFileTypeId(@Nullable FileType fileType) throws StorageException {
    if (fileType == null) return 0;
    try {
      int fileTypeId = getFileTypeId(fileType.getName());
      assert fileTypeId < Short.MAX_VALUE : "fileTypeId overflow";
      return (short) fileTypeId;
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @NotNull
  private Boolean updateIndex(int inputId, short fileTypeId) {
    myLock.writeLock().lock();
    try {
      if (myInMemoryMode.get()) {
        throw new IllegalStateException("file type index should not be updated for unsaved changes");
      }
      else {
        mySnapshot.setAssociation(inputId, fileTypeId);
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
    myForwardIndexController.flush();
  }

  @Override
  public void clear() throws StorageException {
    mySnapshot.clear();
    myForwardIndexController.clear();
  }

  @Override
  public void dispose() {
    try {
      myForwardIndexController.close();
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int keysCountApproximately() {
    return myFileTypeEnumerator.getSize();
  }

  @Override
  public int getFileTypeId(String name) throws IOException {
    return myFileTypeEnumerator.enumerate(name);
  }

  private static class ForwardIndexFileController {
    private static final int ELEMENT_BYTES = Short.BYTES;

    private final @NotNull FileChannel myFileChannel;
    private long myElementsCount;
    private long myModificationsCounter = 0L;
    private final @NotNull ByteBuffer myDataBuffer = ByteBuffer.allocate(ELEMENT_BYTES);

    private ForwardIndexFileController(@NotNull Path storage) throws StorageException {
      try {
        myFileChannel = FileChannel.open(storage, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        long fileSize = myFileChannel.size();
        if (fileSize % ELEMENT_BYTES != 0) {
          LOG.error("file type index is corrupted");
          clear();
          fileSize = 0;
        }
        myElementsCount = fileSize / ELEMENT_BYTES;
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    }

    public long getModificationStamp() {
      return myModificationsCounter;
    }

    public short get(int index) throws StorageException {
      try {
        myDataBuffer.clear();
        int bytesLeft = ELEMENT_BYTES;
        while (bytesLeft > 0) {
          int result = myFileChannel.read(myDataBuffer, (long)index * ELEMENT_BYTES);
          if (result == -1 && bytesLeft == ELEMENT_BYTES)
            return 0; // read after EOF
          if (result == -1)
            throw new StorageException("forward file type index is corrupted");
          bytesLeft -= result;
        }
        myDataBuffer.flip();
        // LOG.warn("get " + index + " -> " + result);
        return myDataBuffer.getShort();
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    }

    public void set(int index, short value) throws StorageException {
      try {
        // LOG.warn("set " + index + " -> " + value);
        ensureSize(index + 1);
        myDataBuffer.clear();
        myDataBuffer.putShort(value);
        myDataBuffer.flip();
        int bytesWritten = 0;
        while (bytesWritten < ELEMENT_BYTES) {
          bytesWritten += myFileChannel.write(myDataBuffer, (long)index * ELEMENT_BYTES + bytesWritten);
        }
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
      myModificationsCounter++;
    }

    private void ensureSize(int indexAfterLastElement) throws StorageException {
      if (myElementsCount >= indexAfterLastElement)
        return;
      try {
        final int zeroBufSize = 512;
        ByteBuffer zeroBuf = ByteBuffer.allocate(zeroBufSize);
        while (myElementsCount < indexAfterLastElement) {
          myFileChannel.write(zeroBuf, myElementsCount * ELEMENT_BYTES + zeroBuf.position());
          if (!zeroBuf.hasRemaining()) {
            zeroBuf.position(0);
            myElementsCount += zeroBufSize / ELEMENT_BYTES;
          }
        }
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    }

    @FunctionalInterface
    public interface EntriesProcessor {
      void process(int inputId, short data) throws StorageException;
    }

    public void processEntries(@NotNull EntriesProcessor processor) throws StorageException {
      try {
        boolean isReadAction = ApplicationManager.getApplication().isReadAccessAllowed();

        final int bufferSize = 1024;
        final ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        for (int i = 0; i < myElementsCount;) {
          if (isReadAction) {
            ProgressManager.checkCanceled();
          }
          buffer.clear();
          while (buffer.position() < bufferSize) {
            int cur = myFileChannel.read(buffer, (long)i * ELEMENT_BYTES + buffer.position());
            if (cur == -1) break; // EOF
          }
          buffer.flip();
          if (buffer.limit() % ELEMENT_BYTES != 0)
            throw new StorageException("forward index is corrupted");
          while (buffer.position() < buffer.limit()) {
            processor.process(i, buffer.getShort());
            i++;
          }
        }
      } catch (IOException e) {
        throw new StorageException(e);
      }
    }

    public void clear() throws StorageException {
      try {
        myFileChannel.truncate(0);
        myElementsCount = 0;
        myModificationsCounter++;
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    }

    public void flush() throws StorageException {
      try {
        myFileChannel.force(true);
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    }

    public void close() throws StorageException {
      try {
        myFileChannel.close();
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    }
  }

  private interface IntShortIndex {
    void setAssociation(int inputId, short data) throws StorageException;
  }

  private static class MemorySnapshot implements MappedFileTypeIndex.IntShortIndex {
    private final @NotNull Int2ObjectMap<BitSet> myInvertedIndex;
    private final @NotNull ForwardIndexFileController myForwardIndex;

    private MemorySnapshot(@NotNull Int2ObjectMap<BitSet> invertedIndex, @NotNull ForwardIndexFileController forwardIndex) {
      myInvertedIndex = invertedIndex;
      myForwardIndex = forwardIndex;
    }

    @Override
    public synchronized void setAssociation(int inputId, short data) throws StorageException {
      short indexedData = getIndexedData(inputId);
      if (indexedData != 0) {
        BitSet indexedSet = myInvertedIndex.get(indexedData);
        assert indexedSet != null;
        indexedSet.clear(inputId);
      }
      setForwardIndexData(myForwardIndex, inputId, data);
      if (data != 0) {
        myInvertedIndex.computeIfAbsent(data, __ -> new BitSet()).set(inputId);
      }
    }

    public synchronized @NotNull MappedFileTypeIndex.IntSeq getFileIds(int data) {
      BitSet fileIds = myInvertedIndex.get(data);
      return fileIds == null ? MappedFileTypeIndex.IntSeq.EMPTY : new MappedFileTypeIndex.IntSeq.FromBitSet(fileIds);
    }

    public synchronized short getIndexedData(int inputId) throws StorageException {
      return myForwardIndex.get(inputId);
    }

    private void clear() throws StorageException {
      myInvertedIndex.clear();
      myForwardIndex.clear();
    }
  }

  private static @NotNull MappedFileTypeIndex.MemorySnapshot loadIndexToMemory(@NotNull ForwardIndexFileController forwardIndex)
    throws StorageException {
    Int2ObjectMap<BitSet> invertedIndex = new Int2ObjectOpenHashMap<>();
    forwardIndex.processEntries((inputId, data) -> {
      if (data != 0) {
        setForwardIndexData(forwardIndex, inputId, data);
        invertedIndex.computeIfAbsent(data, __ -> new BitSet()).set(inputId);
      }
    });
    return new MappedFileTypeIndex.MemorySnapshot(invertedIndex, forwardIndex);
  }

  private static void setForwardIndexData(@NotNull ForwardIndexFileController forwardIndex, int inputId, short data)
    throws StorageException
  {
    forwardIndex.set(inputId, data);
  }

  private interface IntSeq {
    void forEach(@NotNull IntConsumer consumer);

    @NotNull MappedFileTypeIndex.IntSeq copy();

    class FromBitSet implements MappedFileTypeIndex.IntSeq {
      private final BitSet myBitSet;

      private FromBitSet(@NotNull BitSet set) { myBitSet = set; }

      @Override
      public void forEach(@NotNull IntConsumer consumer) {
        myBitSet.stream().forEach(consumer);
      }

      @Override
      public @NotNull MappedFileTypeIndex.IntSeq copy() {
        return new FromBitSet((BitSet)myBitSet.clone());
      }
    }

    MappedFileTypeIndex.IntSeq EMPTY = new MappedFileTypeIndex.IntSeq() {
      @Override
      public void forEach(@NotNull IntConsumer consumer) { }

      @Override
      public @NotNull MappedFileTypeIndex.IntSeq copy() {
        return this;
      }
    };
  }
}
