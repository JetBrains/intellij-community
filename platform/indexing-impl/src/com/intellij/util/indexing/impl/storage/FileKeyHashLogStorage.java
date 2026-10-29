// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.keyStorage.AppendableStorageBackedByResizableMappedFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/// A file storage for events that add or remove mappings between key hashes and file IDs.
/// It uses [AppendableStorageBackedByResizableMappedFile] and owns the mapping files and their locks
@ApiStatus.Internal
public final class FileKeyHashLogStorage implements KeyHashLogStorage {
  private static final Logger LOG = Logger.getInstance(FileKeyHashLogStorage.class);

  private final @NotNull Path dataFile;
  private final @NotNull Path compactionMarker;

  private final @Nullable StorageLockContext storageLockContext;
  private final @NotNull AppendableStorageBackedByResizableMappedFile<int[]> mapping;

  private boolean closed;

  public FileKeyHashLogStorage(@NotNull Path baseStorageFile,
                               @Nullable StorageLockContext storageLockContext) throws IOException {
    this.storageLockContext = storageLockContext;
    dataFile = baseStorageFile.resolveSibling(baseStorageFile.getFileName() + ".project");
    compactionMarker = dataFile.resolveSibling(dataFile.getFileName() + ".require.compaction");
    mapping = openMapping(dataFile, isCompactionRequired() ? 0 : 4096);
  }

  @Override
  public void append(int keyHash, int signedInputId) throws IOException {
    withWriteLock(() -> mapping.append(new int[]{keyHash, signedInputId}));
  }

  @Override
  public @NotNull Position endPosition() {
    mapping.lockRead();
    try {
      return new Position(mapping.getCurrentLength());
    }
    finally {
      mapping.unlockRead();
    }
  }

  @Override
  public boolean isPositionInRange(@NotNull Position position, @NotNull Position endPosition) {
    return position.value() >= 0 && position.value() <= endPosition.value() && endPosition.value() <= Integer.MAX_VALUE;
  }

  @Override
  public boolean scan(@NotNull Position from, @NotNull Position to, @NotNull Processor processor) throws IOException {
    if (!isPositionInRange(from, to) || !isPositionInRange(to, endPosition())) throw new IOException("Invalid log range");
    int offset = offset(from);
    int end = offset(to);
    if (offset == end) return true;

    if (offset == 0) {
      int[] scannedUpTo = {0};
      boolean[] completed = {true};
      // processAll() takes a write lock to force the append buffer. A read lock here would cause a deadlock.
      mapping.processAll((recordOffset, pair) -> {
        if (!completed[0]) return false;
        // The mapped input stream can restart. Do not deliver an event twice.
        if (recordOffset < scannedUpTo[0]) return true;
        if (recordOffset >= end) return false;
        if (recordOffset != scannedUpTo[0]) throw new IOException("The log scan skipped a record: " + scannedUpTo[0]);
        scannedUpTo[0] = checkRecordEnd(recordOffset, pair, end);
        if (!processor.process(pair[0], pair[1])) {
          completed[0] = false;
          return false;
        }
        return true;
      });
      if (completed[0] && scannedUpTo[0] != end) throw new IOException("The log scan did not reach its final position: " + end);
      return completed[0];
    }

    mapping.lockRead();
    try {
      while (offset < end) {
        int[] pair = mapping.read(offset, false);
        offset = checkRecordEnd(offset, pair, end);
        if (!processor.process(pair[0], pair[1])) return false;
      }
      return true;
    }
    finally {
      mapping.unlockRead();
    }
  }

  @Override
  public boolean isCompactionRequired() {
    return Files.exists(compactionMarker);
  }

  @Override
  public void setCompactionRequired() {
    if (Files.exists(compactionMarker)) return;
    try {
      Files.createDirectories(compactionMarker.getParent());
      Files.createFile(compactionMarker);
    }
    catch (FileAlreadyExistsException ignored) {
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  public void clear() throws IOException {
    withWriteLock(() -> {
      mapping.clear();
      try {
        Files.delete(compactionMarker);
      }
      catch (IOException ignored) {
      }
    });
  }

  @Override
  public String toString() {
    return dataFile.toString();
  }

  @Override
  public void flush() throws IOException {
    withWriteLock(() -> {
      if (mapping.isDirty()) {
        mapping.force();
      }
    });
  }

  @Override
  public void close() throws IOException {
    if (!closed) {
      withWriteLock(mapping::close);
      closed = true;
    }
  }

  private @NotNull AppendableStorageBackedByResizableMappedFile<int[]> openMapping(@NotNull Path file, int size) throws IOException {
    return new AppendableStorageBackedByResizableMappedFile<>(file, size, storageLockContext, IOUtil.MiB, true,
                                                              IntPairExternalizer.INSTANCE);
  }

  private void withWriteLock(@NotNull ThrowableRunnable<? extends IOException> action) throws IOException {
    mapping.lockWrite();
    try {
      action.run();
    }
    finally {
      mapping.unlockWrite();
    }
  }

  private static int offset(@NotNull Position position) {
    return Math.toIntExact(position.value());
  }

  private static int checkRecordEnd(int offset, int @NotNull [] pair, int end) throws IOException {
    long next = (long)offset + DataInputOutputUtil.sizeOfVarint(pair[0]) + DataInputOutputUtil.sizeOfVarint(pair[1]);
    if (next > end) throw new IOException("The log position splits a record: " + end);
    return (int)next;
  }

  private static final class IntPairExternalizer implements DataExternalizer<int[]> {
    private static final IntPairExternalizer INSTANCE = new IntPairExternalizer();
    // The array stays inside the storage. Callbacks receive its two values.
    private static final ThreadLocal<int[]> PAIR = ThreadLocal.withInitial(() -> new int[2]);

    @Override
    public void save(@NotNull DataOutput out, int[] value) throws IOException {
      DataInputOutputUtil.writeINT(out, value[0]);
      DataInputOutputUtil.writeINT(out, value[1]);
    }

    @Override
    public int[] read(@NotNull DataInput input) throws IOException {
      int[] result = PAIR.get();
      result[0] = DataInputOutputUtil.readINT(input);
      result[1] = DataInputOutputUtil.readINT(input);
      return result;
    }
  }
}
