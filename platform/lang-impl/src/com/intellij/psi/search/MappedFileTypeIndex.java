// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.SpecializedFileAttributes;
import com.intellij.openapi.vfs.newvfs.persistent.dev.MappedFileStorageHelper;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.containers.*;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import com.intellij.util.io.ResilientFileChannel;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;

import static com.intellij.util.SystemProperties.getBooleanProperty;
import static com.intellij.util.SystemProperties.getIntProperty;

public final class MappedFileTypeIndex extends FileTypeIndexImplBase {
  private static final Logger LOG = Logger.getInstance(MappedFileTypeIndex.class);

  private static final int INVERTED_INDEX_SIZE_THRESHOLD = getIntProperty("mapped.file.type.index.inverse.upgrade.threshold", 256);

  /** Use experimental forward-index implementation over fast (mapped) file attributes? */
  private static final boolean FORWARD_INDEX_OVER_MMAPPED_ATTRIBUTE =
    getBooleanProperty("mapped-file-type-index.forward-index-over-mapped-attribute", true);

  private final @NotNull MappedFileTypeIndex.IndexDataController myDataController;

  public MappedFileTypeIndex(@NotNull FileBasedIndexExtension<FileType, Void> extension) throws IOException, StorageException {
    super(extension);

    var storageFile = getStorageFile();
    myDataController = loadIndexToMemory(storageFile.resolveSibling(storageFile.getFileName().toString() + ".index"), id -> {
      notifyInvertedIndexChangedForFileTypeId(id);
    });
  }

  private static short checkFileTypeIdIsShort(int fileTypeId) {
    assert fileTypeId < Short.MAX_VALUE : "file type id = " + fileTypeId;
    return (short)fileTypeId;
  }

  @Override
  public long getModificationStamp() {
    return myDataController.getModificationStamp();
  }

  @Override
  protected void processFileIdsForFileTypeId(int fileTypeId, @NotNull IntConsumer consumer) {
    for (IntIdsIterator it = myDataController.getFileIds(checkFileTypeIdIsShort(fileTypeId)); it.hasNext(); ) {
      consumer.accept(it.next());
    }
  }

  @Override
  public @NotNull Computable<Boolean> mapInputAndPrepareUpdate(int inputId, @Nullable FileContent content) {
    try {
      int fileTypeId = getFileTypeId(content == null ? null : content.getFileType());
      return () -> updateIndex(inputId, checkFileTypeIdIsShort(fileTypeId));
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected int getIndexedFileTypeId(int fileId) throws StorageException {
    myLock.readLock().lock();
    try {
      return myDataController.getIndexedData(fileId);
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  private @NotNull Boolean updateIndex(int inputId, short fileTypeId) {
    myLock.writeLock().lock();
    try {
      if (myInMemoryMode.get()) {
        throw new IllegalStateException("file type index should not be updated for unsaved changes");
      }
      else {
        myDataController.setAssociation(inputId, fileTypeId);
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
    myDataController.flush();
  }

  @Override
  public boolean isDirty() {
    return myDataController.isDirty();
  }

  @Override
  public void clear() throws StorageException {
    myDataController.clear();
  }

  @Override
  public void dispose() {
    try {
      myDataController.close();
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
  }

  private static final class IndexDataController {
    private final @NotNull Int2ObjectMap<RandomAccessIntContainer> myInvertedIndex;
    private final @NotNull MappedFileTypeIndex.IndexDataController.ForwardIndexFileController myForwardIndex;
    private final @NotNull IntConsumer myInvertedIndexChangeCallback;

    private IndexDataController(@NotNull Int2ObjectMap<RandomAccessIntContainer> invertedIndex,
                                @NotNull MappedFileTypeIndex.IndexDataController.ForwardIndexFileController forwardIndex,
                                @NotNull IntConsumer invertedIndexChangeCallback) {
      myInvertedIndex = invertedIndex;
      myForwardIndex = forwardIndex;
      myInvertedIndexChangeCallback = invertedIndexChangeCallback;
    }
    //FIXME RC: why do we use synchronized in this class? -- all the method calls are protected by RWLock up the
    // callstack anyway

    public void setAssociation(int inputId, short data) throws StorageException {
      short indexedData = getIndexedData(inputId);
      if (indexedData != 0) {
        var indexedSet = myInvertedIndex.get(indexedData);
        assert indexedSet != null;
        indexedSet.remove(inputId);
      }
      myForwardIndex.set(inputId, data);
      if (data != 0) {
        myInvertedIndex.computeIfAbsent(data, __ -> createContainerForInvertedIndex()).add(inputId);
      }
      triggerOnInvertedIndexChangeCallback(data, indexedData);
    }

    private void triggerOnInvertedIndexChangeCallback(short newData, short oldData) {
      if (oldData != newData) {
        if (oldData != 0) {
          myInvertedIndexChangeCallback.accept(oldData);
        }
        if (newData != 0) {
          myInvertedIndexChangeCallback.accept(newData);
        }
      }
    }

    public @NotNull IntIdsIterator getFileIds(int data) {
      RandomAccessIntContainer fileIds = myInvertedIndex.get(data);
      return fileIds == null ? ValueContainerImpl.EMPTY_ITERATOR : fileIds.intIterator();
    }

    public short getIndexedData(int inputId) throws StorageException {
      return myForwardIndex.get(inputId);
    }

    public long getModificationStamp() {
      return myForwardIndex.modificationsCounter();
    }

    public void clear() throws StorageException {
      myInvertedIndex.clear();
      myForwardIndex.clear();
    }

    public void flush() throws StorageException {
      myForwardIndex.flush();
    }

    public boolean isDirty() {
      return myForwardIndex.isDirty();
    }

    public void close() throws StorageException {
      myForwardIndex.close();
    }


    public interface ForwardIndexFileController {
      long modificationsCounter();

      short get(int inputId) throws StorageException;

      void set(int inputId, short value) throws StorageException;

      void processEntries(@NotNull EntriesProcessor processor) throws StorageException;

      void clear() throws StorageException;

      void flush() throws StorageException;

      boolean isDirty();

      void close() throws StorageException;

      @FunctionalInterface
      interface EntriesProcessor {
        void process(int inputId, short data) throws StorageException;
      }
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

  private static @NotNull MappedFileTypeIndex.IndexDataController loadIndexToMemory(
    @NotNull Path forwardIndexStorageFile,
    @NotNull IntConsumer invertedIndexChangeCallback) throws StorageException {

    var forwardIndex = FORWARD_INDEX_OVER_MMAPPED_ATTRIBUTE ?
                       new ForwardIndexFileControllerOverMappedFile() :
                       new ForwardIndexFileControllerOverFile(forwardIndexStorageFile);
    Int2ObjectMap<RandomAccessIntContainer> invertedIndex = new Int2ObjectOpenHashMap<>();
    forwardIndex.processEntries((inputId, data) -> {
      if (data != 0) {
        invertedIndex.computeIfAbsent(data, __ -> createContainerForInvertedIndex()).add(inputId);
      }
    });
    return new IndexDataController(invertedIndex, forwardIndex, invertedIndexChangeCallback);
  }

  private static final class ForwardIndexFileControllerOverFile implements IndexDataController.ForwardIndexFileController {
    private static final int ELEMENT_BYTES = Short.BYTES;
    private static final int DEFAULT_FILE_ALLOCATION_BYTES = 512;
    private static final int DEFAULT_FULL_SCAN_BUFFER_BYTES = 1024;

    private final @NotNull ResilientFileChannel myFileChannel;
    private volatile long myElementsCount;
    private volatile long myModificationsCounter = 0L;
    private final @NotNull ByteBuffer myDataBuffer = ByteBuffer.allocate(ELEMENT_BYTES);

    private ForwardIndexFileControllerOverFile(@NotNull Path storage) throws StorageException {
      try {
        myFileChannel = new ResilientFileChannel(storage, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        long fileSize = myFileChannel.size();
        if (fileSize % ELEMENT_BYTES != 0) {
          LOG.error("file type index is corrupted");
          clear();
          fileSize = 0;
        }
        myElementsCount = fileSize / ELEMENT_BYTES;
      }
      catch (IOException e) {
        throw closeWithException(new StorageException(e));
      }
    }

    private static long offsetInFile(long inputId) {
      return inputId * ELEMENT_BYTES;
    }

    @Override
    public long modificationsCounter() {
      return myModificationsCounter;
    }

    @Override
    public short get(int inputId) throws StorageException {
      try {
        myDataBuffer.clear();
        int bytesLeft = ELEMENT_BYTES;
        while (bytesLeft > 0) {
          int result = myFileChannel.read(myDataBuffer, offsetInFile(inputId) + myDataBuffer.position());
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
        throw closeWithException(new StorageException(e));
      }
    }

    @Override
    public void set(int inputId, short value) throws StorageException {
      try {
        ensureCapacity(inputId);
        myDataBuffer.clear();
        myDataBuffer.putShort(value);
        myDataBuffer.flip();
        int bytesWritten = 0;
        while (bytesWritten < ELEMENT_BYTES) {
          bytesWritten += myFileChannel.write(myDataBuffer, offsetInFile(inputId) + bytesWritten);
        }
      }
      catch (IOException e) {
        throw closeWithException(new StorageException(e));
      }
      //noinspection NonAtomicOperationOnVolatileField
      myModificationsCounter++;
    }

    private void ensureCapacity(int inputIdToStore) throws StorageException {
      final int elementsToStore = inputIdToStore + 1;
      if (myElementsCount >= elementsToStore) {
        return;
      }
      try {
        final int zeroBufSize = DEFAULT_FILE_ALLOCATION_BYTES;
        ByteBuffer zeroBuf = ByteBuffer.allocate(zeroBufSize);
        while (myElementsCount < elementsToStore) {
          myFileChannel.write(zeroBuf, offsetInFile(myElementsCount) + zeroBuf.position());
          if (!zeroBuf.hasRemaining()) {
            zeroBuf.position(0);
            //noinspection NonAtomicOperationOnVolatileField
            myElementsCount += zeroBufSize / ELEMENT_BYTES;
          }
        }
      }
      catch (IOException e) {
        throw closeWithException(new StorageException(e));
      }
    }

    @Override
    public void processEntries(@NotNull EntriesProcessor processor) throws StorageException {
      try {
        boolean isCheckCanceledNeeded = LoadingState.COMPONENTS_LOADED.isOccurred() &&
                                        ApplicationManager.getApplication().isReadAccessAllowed();

        final int bufferSize = DEFAULT_FULL_SCAN_BUFFER_BYTES;
        final ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        for (int i = 0; i < myElementsCount; ) {
          if (isCheckCanceledNeeded) {
            ProgressManager.checkCanceled();
          }
          buffer.clear();
          while (buffer.position() < bufferSize) {
            int cur = myFileChannel.read(buffer, offsetInFile(i) + buffer.position());
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
        throw closeWithException(new StorageException(e));
      }
    }

    @Override
    public void clear() throws StorageException {
      try {
        myFileChannel.truncate(0);
        myElementsCount = 0;
        //noinspection NonAtomicOperationOnVolatileField
        myModificationsCounter++;
      }
      catch (IOException e) {
        throw closeWithException(new StorageException(e));
      }
    }

    @Override
    public void flush() throws StorageException {
      try {
        myFileChannel.force(true);
      }
      catch (IOException e) {
        throw closeWithException(new StorageException(e));
      }
    }

    @Override
    public boolean isDirty() {
      //TODO
      return false;
    }

    @Override
    public void close() throws StorageException {
      try {
        myFileChannel.close();
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    }

    private StorageException closeWithException(StorageException e) {
      try {
        myFileChannel.close();
      }
      catch (IOException ioe) {
        e.addSuppressed(ioe);
      }
      return e;
    }
  }

  /**
   * 'OverMappedFile' is a bit of abstraction leak, since implementation really uses specialized
   * FileAttribute ({@link SpecializedFileAttributes#specializeAsFastShort(FSRecordsImpl, FileAttribute)}).
   * But we know that specialization uses memory-mapped file under the hood, and this is
   * that really important here.
   */
  private static final class ForwardIndexFileControllerOverMappedFile implements IndexDataController.ForwardIndexFileController {

    private static final String STORAGE_NAME = "filetype.index";
    private static final int BINARY_FORMAT_VERSION = 1;

    private static final int FIELD_WIDTH = Short.BYTES;
    private static final int FIELD_OFFSET = 0;

    /**
     * FIXME RC: it MUST be set true all the time -- it should _never_ be _any_ fileId in use outside (0..maxAllocatedId].
     * False is a temporary backward compatibility option: it seems like in some use-cases somehow the constraint
     * is violated, but we have no time/hands to to find out why. Investigate, fix, and remove the flag
     */
    public static final boolean CHECK_FILE_ID_BELOW_MAX = getBooleanProperty("MappedFileTypeIndex.CHECK_FILE_ID_BELOW_MAX", false);

    private final MappedFileStorageHelper storage;

    private final AtomicLong modificationsCounter = new AtomicLong(0);

    private ForwardIndexFileControllerOverMappedFile() throws StorageException {
      try {
        storage = MappedFileStorageHelper.openHelperAndVerifyVersions(
          FSRecords.getInstance(),
          STORAGE_NAME,
          BINARY_FORMAT_VERSION,
          FIELD_WIDTH,
          CHECK_FILE_ID_BELOW_MAX
        );
      }
      catch (IOException e) {
        throw new StorageException("Can't open storage [" + STORAGE_NAME + "]", e);
      }
    }

    @Override
    public long modificationsCounter() {
      return modificationsCounter.get();
    }

    @Override
    public short get(int inputId) throws StorageException {
      try {
        return readImpl(inputId);
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    }

    @Override
    public void set(int inputId, short value) throws StorageException {
      try {
        writeImpl(inputId, value);
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
      modificationsCounter.incrementAndGet();
    }

    @Override
    public void processEntries(@NotNull EntriesProcessor processor) throws StorageException {
      try {
        boolean isReadAction = ApplicationManager.getApplication().isReadAccessAllowed();
        int maxAllocatedID = FSRecords.getInstance().connection().getRecords().maxAllocatedID();
        for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
          if (isReadAction) {
            ProgressManager.checkCanceled();
          }
          short value = readImpl(fileId);
          processor.process(fileId, value);
        }
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    }

    @Override
    public void clear() throws StorageException {
      try {
        storage.clearRecords();
        modificationsCounter.incrementAndGet();
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    }

    @Override
    public boolean isDirty() {
      //RC: seems like we don't really need it for mmapped impl, since we don't have flush() anyway?
      return false;
    }

    @Override
    public void flush() {
      //RC: seems like we don't need explicit flush for mmapped impl?
    }

    @Override
    public void close() {
      //storage is closed by FSRecordsImpl
    }

    private void writeImpl(int inputId,
                           short value) throws IOException {
      storage.writeShortField(inputId, FIELD_OFFSET, value);
    }

    private short readImpl(int inputId) throws IOException {
      return storage.readShortField(inputId, FIELD_OFFSET);
    }
  }
}
