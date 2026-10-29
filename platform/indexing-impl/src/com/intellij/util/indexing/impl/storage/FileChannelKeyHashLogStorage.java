// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.io.ChannelsAccessor;
import com.intellij.util.io.PageCacheUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/// A file storage for events that add or remove mappings between key hashes and file IDs.
/// It uses fixed-size records and cached file channels. A local lock protects the append buffer and each scan
@ApiStatus.Internal
public final class FileChannelKeyHashLogStorage implements KeyHashLogStorage {
  private static final Logger LOG = Logger.getInstance(FileChannelKeyHashLogStorage.class);
  private static final int RECORD_SIZE = 2 * Integer.BYTES;
  private static final int BUFFER_SIZE = 64 * 1024;

  private final @NotNull Path dataFile;
  private final @NotNull Path compactionMarker;
  private final @NotNull ChannelsAccessor channelsAccessor;
  private final ByteBuffer appendBuffer = ByteBuffer.allocate(BUFFER_SIZE);

  private long writtenLength;
  private boolean closed;

  public FileChannelKeyHashLogStorage(@NotNull Path baseStorageFile) throws IOException {
    this(baseStorageFile, PageCacheUtils.getCachedChannelsAccessor(false));
  }

  public FileChannelKeyHashLogStorage(@NotNull Path baseStorageFile, @NotNull ChannelsAccessor channelsAccessor) throws IOException {
    if (channelsAccessor.isReadOnly()) throw new IllegalArgumentException("The channels accessor must permit writes");
    this.channelsAccessor = channelsAccessor;
    var absoluteBase = baseStorageFile.toAbsolutePath().normalize();
    dataFile = absoluteBase.resolveSibling(absoluteBase.getFileName() + ".project");
    compactionMarker = dataFile.resolveSibling(dataFile.getFileName() + ".require.compaction");
    Files.createDirectories(dataFile.getParent());
    try {
      writtenLength = channelsAccessor.executeOp(dataFile, channel -> channel.size());
      if (writtenLength % RECORD_SIZE != 0) throw new EOFException("The key hash log ends inside a record: " + dataFile);
    }
    catch (IOException | RuntimeException | Error e) {
      try {
        channelsAccessor.closeChannel(dataFile);
      }
      catch (IOException closeError) {
        e.addSuppressed(closeError);
      }
      throw e;
    }
  }

  @Override
  public synchronized void append(int keyHash, int signedInputId) throws IOException {
    checkOpen();
    if (appendBuffer.remaining() < RECORD_SIZE) drainAppendBuffer();
    appendBuffer.putInt(keyHash).putInt(signedInputId);
  }

  @Override
  public synchronized @NotNull Position endPosition() throws IOException {
    checkOpen();
    return new Position(writtenLength + appendBuffer.position());
  }

  @Override
  public boolean isPositionInRange(@NotNull Position position, @NotNull Position endPosition) {
    return position.value() >= 0 && position.value() <= endPosition.value() &&
           position.value() % RECORD_SIZE == 0 && endPosition.value() % RECORD_SIZE == 0;
  }

  @Override
  public synchronized boolean scan(@NotNull Position from, @NotNull Position to, @NotNull Processor processor) throws IOException {
    checkOpen();
    if (!isPositionInRange(from, to) || !isPositionInRange(to, endPosition())) {
      throw new IOException("Invalid log range");
    }
    if (from.equals(to)) {
      return true;
    }
    drainAppendBuffer();

    var buffer = ByteBuffer.allocate((int)Math.min(BUFFER_SIZE, to.value() - from.value()));
    for (long offset = from.value(); offset < to.value(); ) {
      long blockOffset = offset;
      int blockSize = (int)Math.min(buffer.capacity(), to.value() - offset);
      channelsAccessor.executeIdempotentOp(dataFile, channel -> {
        buffer.clear().limit(blockSize);
        while (buffer.hasRemaining()) {
          int read = channel.read(buffer, blockOffset + buffer.position());
          if (read < 0) throw new EOFException("The key hash log ends before the scan position: " + to.value());
          if (read == 0) throw new IOException("The key hash log read made no progress");
        }
        return null;
      });
      buffer.flip();
      // Channel operations can restart. Deliver events only after the block read succeeds.
      while (buffer.hasRemaining()) {
        int keyHash = buffer.getInt();
        int inputId = buffer.getInt();
        if (!processor.process(keyHash, inputId)) return false;
      }
      offset += blockSize;
    }
    return true;
  }

  @Override
  public synchronized boolean isCompactionRequired() {
    return Files.exists(compactionMarker);
  }

  @Override
  public synchronized void setCompactionRequired() {
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
  public synchronized void clear() throws IOException {
    checkOpen();
    channelsAccessor.executeIdempotentOp(dataFile, channel -> {
      channel.truncate(0);
      return null;
    });
    writtenLength = 0;
    appendBuffer.clear();
    try {
      Files.deleteIfExists(compactionMarker);
    }
    catch (IOException ignored) {
    }
  }

  /// Writes the append buffer to the channel without requesting a filesystem sync
  @Override
  public synchronized void flush() throws IOException {
    checkOpen();
    drainAppendBuffer();
  }

  @Override
  public synchronized void close() throws IOException {
    if (closed) return;
    try {
      ExceptionUtil.runAllAndRethrowAllExceptions(
        IOException.class, IOException::new,
        this::drainAppendBuffer,
        () -> channelsAccessor.closeChannel(dataFile)
      );
    }
    finally {
      closed = true;
    }
  }

  @Override
  public String toString() {
    return dataFile.toString();
  }

  /// Uses fixed offsets so a failed write can repeat without appending the same events twice
  private synchronized void drainAppendBuffer() throws IOException {
    int size = appendBuffer.position();
    if (size == 0) return;
    long blockOffset = writtenLength;
    var buffer = appendBuffer.duplicate();
    channelsAccessor.executeIdempotentOp(dataFile, channel -> {
      buffer.position(0).limit(size);
      while (buffer.hasRemaining()) {
        if (channel.write(buffer, blockOffset + buffer.position()) == 0) {
          throw new IOException("The key hash log write made no progress");
        }
      }
      return null;
    });
    writtenLength += size;
    appendBuffer.clear();
  }

  private void checkOpen() throws ClosedChannelException {
    if (closed) throw new ClosedChannelException();
  }
}
