// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/// Stores `(keyHash, [+/-] fileId)` tuples, there '-'-sign identifies removing
@ApiStatus.Internal
public interface KeyHashLogStorage extends Closeable, Flushable {
  /// Identifies a processed prefix. Only the storage interprets the value
  record Position(long value) {
    public static final @NotNull Position START = new Position(0);
  }

  void append(int keyHash, int signedInputId) throws IOException;

  /// @return current end position of a storage
  @NotNull Position endPosition() throws IOException;

  /// @return true when position is between initial and `<= endPosition`
  boolean isPositionInRange(@NotNull Position position, @NotNull Position endPosition);

  /// Processes the events after `from` up to `to`, in order. A false result means the processor stopped the scan
  boolean scan(@NotNull Position from, @NotNull Position to, @NotNull Processor processor) throws IOException;

  boolean isCompactionRequired();

  void setCompactionRequired();

  /// Clears all events and the compaction marker before consumers can use the log
  void clear() throws IOException;

  /// Forces pending changes to persistent storage
  @Override
  void flush() throws IOException;

  @FunctionalInterface
  interface Processor {
    boolean process(int keyHash, int signedInputId) throws IOException;
  }
}
