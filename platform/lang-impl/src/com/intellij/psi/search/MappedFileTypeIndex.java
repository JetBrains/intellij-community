// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.containers.*;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.IntConsumer;

public final class MappedFileTypeIndex extends FileTypeIndexImplBase {
  private static final Logger LOG = Logger.getInstance(MappedFileTypeIndex.class);
  private static final int INVERTED_INDEX_SIZE_THRESHOLD =
    SystemProperties.getIntProperty("mapped.file.type.index.inverse.upgrade.threshold", 256);

  private static class FileDetails {
    public FileType myFileType;
    public String myFileTypeName;

    private FileDetails(FileType fileType, String fileTypeName) {
      myFileType = fileType;
      myFileTypeName = fileTypeName;
    }
  }

  private final @NotNull ConcurrentIntObjectMap<FileDetails> myId2FileDetailsCache =
    ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private final @NotNull MappedFileTypeIndex.MemorySnapshot mySnapshot;
  private final @NotNull ForwardIndexFileController myForwardIndexController;

  private final @NotNull FileTypeIndex.IndexChangeListener myIndexChangedPublisher;

  public MappedFileTypeIndex(@NotNull FileBasedIndexExtension<FileType, Void> extension) throws IOException, StorageException {
    super(extension);

    var storageFile = getStorageFile();
    myForwardIndexController = new ForwardIndexFileController(
      storageFile.resolveSibling(storageFile.getFileName().toString() + ".index")
    );

    myIndexChangedPublisher =
      ApplicationManager.getApplication().getMessageBus().syncPublisher(FileTypeIndex.INDEX_CHANGE_TOPIC);

    mySnapshot = loadIndexToMemory(myForwardIndexController, id -> {
      myIndexChangedPublisher.changedForFileType(getFileTypeById(id));
    });
  }

  @Override
  public long getModificationStamp() {
    return myForwardIndexController.getModificationStamp();
  }

  @Override
  protected void processFileIdsForFileTypeId(int fileTypeId, @NotNull IntConsumer consumer) {
    for (IntIdsIterator it = mySnapshot.getFileIds(fileTypeId); it.hasNext(); ) {
      consumer.accept(it.next());
    }
  }

  @Override
  public @NotNull Computable<Boolean> mapInputAndPrepareUpdate(int inputId, @Nullable FileContent content) {
    try {
      int fileTypeId = getFileTypeId(content == null ? null : content.getFileType());
      assert fileTypeId < Short.MAX_VALUE : "fileTypeId overflow";
      return () -> updateIndex(inputId, (short)fileTypeId);
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
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

  @Override
  public String getFileTypeName(int id) {
    return getFileDetails(id).myFileTypeName;
  }

  @Override
  protected @NotNull FileType getFileTypeById(int id) {
    return getFileDetails(id).myFileType;
  }

  @Override
  protected int getIndexedFileTypeId(int fileId) throws StorageException {
    myLock.readLock().lock();
    try {
      return mySnapshot.getIndexedData(fileId);
    }
    finally {
      myLock.readLock().unlock();
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


  private static class ForwardIndexFileController {
    private static final int ELEMENT_BYTES = Short.BYTES;

    private final @NotNull FileChannel myFileChannel;
    private volatile long myElementsCount;
    private volatile long myModificationsCounter = 0L;
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
          int result = myFileChannel.read(myDataBuffer, (long)index * ELEMENT_BYTES + myDataBuffer.position());
          if (result == -1 && bytesLeft == ELEMENT_BYTES) {
            return 0; // read after EOF
          }
          if (result == -1) {
            throw new StorageException("forward file type index is corrupted");
          }
          bytesLeft -= result;
        }
        myDataBuffer.flip();
        return myDataBuffer.getShort();
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    }

    public void set(int index, short value) throws StorageException {
      try {
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
      //noinspection NonAtomicOperationOnVolatileField
      myModificationsCounter++;
    }

    private void ensureSize(int indexAfterLastElement) throws StorageException {
      if (myElementsCount >= indexAfterLastElement) {
        return;
      }
      try {
        final int zeroBufSize = 512;
        ByteBuffer zeroBuf = ByteBuffer.allocate(zeroBufSize);
        while (myElementsCount < indexAfterLastElement) {
          myFileChannel.write(zeroBuf, myElementsCount * ELEMENT_BYTES + zeroBuf.position());
          if (!zeroBuf.hasRemaining()) {
            zeroBuf.position(0);
            //noinspection NonAtomicOperationOnVolatileField
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
        for (int i = 0; i < myElementsCount; ) {
          if (isReadAction) {
            ProgressManager.checkCanceled();
          }
          buffer.clear();
          while (buffer.position() < bufferSize) {
            int cur = myFileChannel.read(buffer, (long)i * ELEMENT_BYTES + buffer.position());
            if (cur == -1) break; // EOF
          }
          buffer.flip();
          if (buffer.limit() % ELEMENT_BYTES != 0) {
            throw new StorageException("forward index is corrupted");
          }
          while (buffer.position() < buffer.limit()) {
            processor.process(i, buffer.getShort());
            i++;
          }
        }
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    }

    public void clear() throws StorageException {
      try {
        myFileChannel.truncate(0);
        myElementsCount = 0;
        //noinspection NonAtomicOperationOnVolatileField
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

    private final @NotNull Int2ObjectMap<RandomAccessIntContainer> myInvertedIndex;
    private final @NotNull ForwardIndexFileController myForwardIndex;
    private final @NotNull IntConsumer myInvertedIndexChangeCallback;

    private MemorySnapshot(@NotNull Int2ObjectMap<RandomAccessIntContainer> invertedIndex,
                           @NotNull ForwardIndexFileController forwardIndex,
                           @NotNull IntConsumer invertedIndexChangeCallback) {
      myInvertedIndex = invertedIndex;
      myForwardIndex = forwardIndex;
      myInvertedIndexChangeCallback = invertedIndexChangeCallback;
    }

    @Override
    public synchronized void setAssociation(int inputId, short data) throws StorageException {
      short indexedData = getIndexedData(inputId);
      if (indexedData != 0) {
        var indexedSet = myInvertedIndex.get(indexedData);
        assert indexedSet != null;
        indexedSet.remove(inputId);
      }
      setForwardIndexData(myForwardIndex, inputId, data);
      if (data != 0) {
        myInvertedIndex.computeIfAbsent(data, __ -> createContainerForInvertedIndex()).add(inputId);
      }
      notifyInvertedIndexChanged(data, indexedData);
    }

    private void notifyInvertedIndexChanged(short newData, short oldData) {
      if (oldData != newData) {
        if (oldData != 0) {
          myInvertedIndexChangeCallback.accept(oldData);
        }
        if (newData != 0) {
          myInvertedIndexChangeCallback.accept(newData);
        }
      }
    }

    public synchronized @NotNull IntIdsIterator getFileIds(int data) {
      RandomAccessIntContainer fileIds = myInvertedIndex.get(data);
      return fileIds == null ? ValueContainerImpl.EMPTY_ITERATOR : fileIds.intIterator();
    }

    public synchronized short getIndexedData(int inputId) throws StorageException {
      return myForwardIndex.get(inputId);
    }

    private void clear() throws StorageException {
      myInvertedIndex.clear();
      myForwardIndex.clear();
    }
  }

  private static RandomAccessIntContainer createContainerForInvertedIndex() {
    return new UpgradableRandomAccessIntContainer<>(
      INVERTED_INDEX_SIZE_THRESHOLD,
      () -> {
        return new IntHashSetAsRAIntContainer(INVERTED_INDEX_SIZE_THRESHOLD, Hash.DEFAULT_LOAD_FACTOR);
      },
      (container) -> {
        // calculate needed capacity so there are less memory allocations
        int maxId = 0;
        for (IntIdsIterator it = container.intIterator(); it.hasNext(); ) {
          int id = it.next();
          if (maxId < id) maxId = id;
        }
        return new BitSetAsRAIntContainer(maxId + 1);
      }
    );
  }

  private static @NotNull MappedFileTypeIndex.MemorySnapshot loadIndexToMemory(@NotNull ForwardIndexFileController forwardIndex,
                                                                               @NotNull IntConsumer invertedIndexChangeCallback)
    throws StorageException {
    Int2ObjectMap<RandomAccessIntContainer> invertedIndex = new Int2ObjectOpenHashMap<>();
    forwardIndex.processEntries((inputId, data) -> {
      if (data != 0) {
        setForwardIndexData(forwardIndex, inputId, data);
        invertedIndex.computeIfAbsent(data, __ -> createContainerForInvertedIndex()).add(inputId);
      }
    });
    invertedIndex.forEach((id, __) -> {
      invertedIndexChangeCallback.accept(id);
    });
    return new MappedFileTypeIndex.MemorySnapshot(invertedIndex, forwardIndex, invertedIndexChangeCallback);
  }

  private static void setForwardIndexData(@NotNull ForwardIndexFileController forwardIndex, int inputId, short data)
    throws StorageException {
    forwardIndex.set(inputId, data);
  }
}
